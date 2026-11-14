package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.service.AIAssistantService.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 对话历史存储（<b>Redis</b>）
 *
 * <p>📌 **2026-11-14 新增**：原先会话历史是
 * {@code private final Map<String, List<ChatMessage>> sessionHistory = new ConcurrentHashMap<>();}
 * —— 纯内存。后果：
 * <ul>
 *   <li><b>服务重启即丢</b> → 用户对话上下文凭空消失；</li>
 *   <li><b>多副本部署时互相看不见</b> → 同一用户的请求被负载均衡打到不同副本，
 *       上下文随机丢失；</li>
 *   <li><b>无上限</b> → Map 无限增长 → <b>内存泄漏</b>（会话越多越大，永不回收）。</li>
 * </ul>
 *
 * <p>→ 现在改成 <b>Redis + TTL</b>：
 * <ul>
 *   <li>跨副本共享（同一个 key，任意副本都能读到）；</li>
 *   <li>自动过期（{@link #SESSION_TTL}），不会无限增长；</li>
 *   <li>每个会话最多保留 {@link #MAX_MESSAGES_PER_SESSION} 条，超出丢最旧的。</li>
 * </ul>
 */
@Slf4j
@Component
public class ChatSessionStore {

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "erp:ai:chat:";

    /** 会话过期时间（2 小时）—— 足够覆盖一次连续对话，又不会无限堆积 */
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    /** 每个会话最多保留的消息条数（超出丢最旧的） */
    public static final int MAX_MESSAGES_PER_SESSION = 50;

    private final StringRedisTemplate redisTemplate;

    public ChatSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 读会话历史（按写入顺序，最新的在最后）。
     */
    public List<ChatMessage> load(Long userId, String sessionId) {
        String key = key(userId, sessionId);
        try {
            List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
            if (raw == null || raw.isEmpty()) {
                return new ArrayList<>();
            }
            List<ChatMessage> out = new ArrayList<>(raw.size());
            for (String item : raw) {
                ChatMessage msg = ChatMessageCodec.decode(item);
                if (msg != null) {
                    out.add(msg);
                }
            }
            return out;
        } catch (Exception e) {
            // 📌 Redis 不可用 → **如实降级为空历史**，但必须留痕
            //    （不能让整个聊天功能因为 Redis 挂了就 500）
            log.error("读取对话历史失败（Redis 不可用？）- userId: {}, sessionId: {}", userId, sessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 追加一条消息（超出上限丢最旧的）。
     */
    public void append(Long userId, String sessionId, ChatMessage message) {
        String key = key(userId, sessionId);
        try {
            redisTemplate.opsForList().rightPush(key, ChatMessageCodec.encode(message));
            // 超出上限 → 丢最旧的
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > MAX_MESSAGES_PER_SESSION) {
                redisTemplate.opsForList().trim(key, 0, MAX_MESSAGES_PER_SESSION - 1);
            }
            // 每次写入都续期（活跃会话不过期）
            redisTemplate.expire(key, SESSION_TTL);
        } catch (Exception e) {
            log.error("写入对话历史失败（Redis 不可用？）- userId: {}, sessionId: {}", userId, sessionId, e);
        }
    }

    /**
     * 清空某个会话。
     */
    public void clear(Long userId, String sessionId) {
        try {
            redisTemplate.delete(key(userId, sessionId));
        } catch (Exception e) {
            log.error("清空对话历史失败（Redis 不可用？）- userId: {}, sessionId: {}", userId, sessionId, e);
        }
    }

    /**
     * 活跃会话数（统计用）。
     *
     * <p>📌 用 SCAN 而不是 KEYS —— {@code KEYS} 在大库上会**阻塞 Redis**
     * （生产事故级操作）。</p>
     */
    public long countActiveSessions() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            return keys == null ? 0L : keys.size();
        } catch (Exception e) {
            log.error("统计活跃会话失败（Redis 不可用？）", e);
            return 0L;
        }
    }

    /**
     * 统计活跃用户数（从会话 key 里取 userId 段去重）。
     */
    public long countActiveUsers() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0L;
            }
            java.util.Set<String> users = new java.util.HashSet<>();
            for (String k : keys) {
                // key 形如 erp:ai:chat:{sessionId}:{userId}
                int idx = k.lastIndexOf(':');
                if (idx > 0) {
                    users.add(k.substring(idx + 1));
                }
            }
            return users.size();
        } catch (Exception e) {
            log.error("统计活跃用户失败（Redis 不可用？）", e);
            return 0L;
        }
    }

    private String key(Long userId, String sessionId) {
        return KEY_PREFIX + (sessionId == null ? "default" : sessionId) + ":" + (userId == null ? "0" : userId);
    }
}
