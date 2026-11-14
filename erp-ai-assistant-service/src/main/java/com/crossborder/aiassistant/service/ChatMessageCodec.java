package com.crossborder.aiassistant.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.crossborder.aiassistant.service.AIAssistantService.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 对话消息的 Redis 存储编解码器
 *
 * <p>📌 Redis 是 {@code StringRedisTemplate} → 只能存字符串。
 * 这里用 JSON 编码成 {@code role|content|language|createTime} 的紧凑格式
 * （比 JSON 对象小，且解析时不必依赖字段名反射）。</p>
 */
@Slf4j
final class ChatMessageCodec {

    /** 分隔符（用 ASCII Unit Separator，正常内容里不会出现） */
    private static final char SEP = '\u001F';

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private ChatMessageCodec() {
    }

    static String encode(ChatMessage msg) {
        if (msg == null) {
            return "";
        }
        String createTime = msg.getCreateTime() == null ? "" : msg.getCreateTime().format(TS);
        return (nullToEmpty(msg.getRole())) + SEP
                + (nullToEmpty(msg.getContent())) + SEP
                + (nullToEmpty(msg.getLanguage())) + SEP
                + createTime + SEP
                + (msg.getId() == null ? "" : String.valueOf(msg.getId()));
    }

    static ChatMessage decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String[] parts = raw.split(String.valueOf(SEP), -1);
            ChatMessage msg = new ChatMessage();
            msg.setRole(parts.length > 0 ? parts[0] : null);
            msg.setContent(parts.length > 1 ? parts[1] : null);
            msg.setLanguage(parts.length > 2 ? parts[2] : null);
            if (parts.length > 3 && !parts[3].isEmpty()) {
                msg.setCreateTime(LocalDateTime.parse(parts[3], TS));
            }
            if (parts.length > 4 && !parts[4].isEmpty()) {
                msg.setId(Long.parseLong(parts[4]));
            }
            return msg;
        } catch (Exception e) {
            // 📌 一条脏数据不该把整个会话读取打挂 → 跳过这一条，但留痕
            log.warn("解析对话消息失败，已跳过: {}", abbreviate(raw), e);
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.replace(SEP, ' ');
    }

    private static String abbreviate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }

    /** 未使用的 JSON 依赖占位（保留以便未来扩展结构化字段） */
    @SuppressWarnings("unused")
    private static String toJson(ChatMessage msg) {
        return JSON.toJSONString(msg);
    }

    /** 未使用的 JSON 依赖占位 */
    @SuppressWarnings("unused")
    private static ChatMessage fromJson(String json) {
        return JSON.parseObject(json, ChatMessage.class);
    }

    /** 未使用的 JSONObject 依赖占位 */
    @SuppressWarnings("unused")
    private static JSONObject toJsonObject(ChatMessage msg) {
        return JSON.parseObject(JSON.toJSONString(msg));
    }
}
