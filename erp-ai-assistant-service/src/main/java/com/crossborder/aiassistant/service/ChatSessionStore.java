package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.service.AIAssistantService.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * SCAN 每批拉取的 key 数量。
     *
     * <p>📌 太小 → 往返次数暴涨（网络 RTT × 批数）；太大 → 单次占用连接过久。
     * 1000 是 Redis 社区常用值。</p>
     */
    private static final int SCAN_BATCH_SIZE = 1000;

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
     * <p>📌 **2026-11-20 修正（真实缺陷）**：原实现是
     * {@code redisTemplate.keys(KEY_PREFIX + "*")} —— 而**注释却写着「用 SCAN 而不是 KEYS」**。
     * 代码和注释说的根本不是一回事：
     * <ul>
     *   <li>{@code KEYS} 在 Redis 上是 <b>O(N) 且阻塞主线程</b>：遍历全部 key 期间，
     *       Redis <b>不处理任何其他命令</b> → 大库上一个慢查询就把整个 Redis 拖死
     *       （生产事故级操作）；</li>
     *   <li>本项目 Redis 是<b>共享实例</b>（database 2，但同实例还有其他 db/服务），
     *       {@code KEYS *} 的代价由<b>所有业务方</b>共同承担。</li>
     * </ul>
     * → 现在改成 {@link #scanKeys()}：用 {@code SCAN} 游标分批取，
     *   每批只短暂持有连接，<b>不阻塞 Redis 主线程</b>。</p>
     */
    public long countActiveSessions() {
        try {
            long count = 0L;
            for (String k : scanKeys()) {
                if (k != null) {
                    count++;
                }
            }
            return count;
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
            java.util.Set<String> users = new java.util.HashSet<>();
            for (String k : scanKeys()) {
                if (k == null) {
                    continue;
                }
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

    /**
     * 用 SCAN 游标分批扫描本服务前缀下的 key（<b>不阻塞 Redis</b>）。
     *
     * <p>📌 用 {@code redisTemplate.execute(RedisCallback)} 走底层连接拿
     * {@code SCAN} + {@code COUNT}，而不是 Spring 的 {@code scan()} 抽象
     * —— 后者每批都要新建一次回调、且默认 count 太小（10）→ 往返次数暴涨。</p>
     */
    private List<String> scanKeys() {
        List<String> all = new ArrayList<>();
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            try (org.springframework.data.redis.connection.RedisConnection c = connection) {
                // SCAN cursor MATCH pattern COUNT batchSize
                org.springframework.data.redis.core.Cursor<byte[]> cursor = c.scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(KEY_PREFIX + "*")
                                .count(SCAN_BATCH_SIZE)
                                .build());
                while (cursor.hasNext()) {
                    byte[] raw = cursor.next();
                    if (raw != null) {
                        all.add(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }
            return null;
        });
        return all;
    }

    private String key(Long userId, String sessionId) {
        return KEY_PREFIX + (sessionId == null ? "default" : sessionId) + ":" + (userId == null ? "0" : userId);
    }
}
