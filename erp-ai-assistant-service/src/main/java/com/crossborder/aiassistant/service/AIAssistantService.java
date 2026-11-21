package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.dto.ChatRequest;
import com.crossborder.aiassistant.dto.ChatResponse;
import com.crossborder.aiassistant.dto.ServiceStatistics;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI智能客服服务
 *
 * 核心功能：
 * 1. 智能对话 - 基于LLM的问答
 * 2. RAG检索增强 - 结合知识库
 * 3. 多语言支持 - 中英文自动切换
 * 4. 对话历史 - 完整的对话追踪
 * 5. 流式响应 - SSE实时输出
 * 6. 情感分析 - 识别客户情绪
 */
public interface AIAssistantService {

    /**
     * 智能对话（单次）
     *
     * @param request 对话请求
     * @return 对话响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 智能对话（流式响应）
     *
     * @param request 对话请求
     * @return 流式响应
     */
    Flux<String> chatStream(ChatRequest request);

    /**
     * 获取对话历史
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 对话历史
     */
    List<ChatMessage> getChatHistory(Long userId, String sessionId);

    /**
     * 清空对话历史
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     */
    void clearChatHistory(Long userId, String sessionId);

    /**
     * 分析情感
     *
     * @param text 文本
     * @return 情感分析结果
     */
    SentimentAnalysis analyzeSentiment(String text);

    /**
     * 分析情感<b>并落库</b>（可带上下文）
     *
     * <p>📌 **2026-11-20 新增**：原 {@link #analyzeSentiment(String)} 只算不存 →
     * {@code sentiment_analysis_log} 表<b>永远 0 行</b>。
     * 这个重载把 {@code userId} / {@code sessionId} 一起带上并落库。</p>
     *
     * @param text     文本
     * @param userId   用户ID（可空）
     * @param sessionId 会话ID（可空）
     * @return 情感分析结果
     */
    SentimentAnalysis analyzeSentiment(String text, Long userId, String sessionId);

    /**
     * 识别意图
     *
     * @param text 用户输入
     * @return 意图识别结果
     */
    IntentRecognition recognizeIntent(String text);

    /**
     * 识别意图<b>并落库</b>（可带上下文）
     *
     * <p>📌 **2026-11-20 新增**：原 {@link #recognizeIntent(String)} 只算不存 →
     * {@code intent_recognition_log} 表<b>永远 0 行</b>。</p>
     *
     * @param text      用户输入
     * @param userId    用户ID（可空）
     * @param sessionId 会话ID（可空）
     * @return 意图识别结果
     */
    IntentRecognition recognizeIntent(String text, Long userId, String sessionId);

    /**
     * 提交用户反馈（落库）
     *
     * <p>📌 **2026-11-20 新增**：{@code user_feedback} 表<b>有 DDL 没代码</b> → 永远 0 行。
     * 提交「有帮助」反馈时还会同步累加对应知识的 {@code helpful_count}。</p>
     *
     * @param feedback 反馈内容
     * @return 落库后的反馈（含自增主键）
     */
    Feedback submitFeedback(Feedback feedback);

    /**
     * 用户反馈 —— 落库实体
     */
    class Feedback {
        private Long id;
        private Long userId;
        private String sessionId;
        private Long messageId;
        /** helpful / not_helpful / incorrect */
        private String feedbackType;
        /** 1~5 */
        private Integer rating;
        private String comment;
        private java.time.LocalDateTime createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
        public String getFeedbackType() { return feedbackType; }
        public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public java.time.LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    }

    /**
     * 添加知识库条目
     *
     * @param knowledge 知识条目
     */
    void addKnowledge(Knowledge knowledge);

    /**
     * 批量添加知识库
     *
     * @param knowledges 知识条目列表
     */
    void batchAddKnowledge(List<Knowledge> knowledges);

    /**
     * 检索知识库
     *
     * @param query 查询文本
     * @param topK 返回前K条
     * @return 相关知识
     */
    List<Knowledge> searchKnowledge(String query, int topK);

    /**
     * 获取服务统计信息
     *
     * @return 服务统计数据
     */
    ServiceStatistics getStatistics();

    /**
     * 重置统计数据
     */
    void resetStatistics();

    /**
     * 对话消息
     */
    class ChatMessage {
        private Long id;
        private Long userId;
        private String sessionId;
        private String role; // user或assistant
        private String content;
        private String language;
        private java.time.LocalDateTime createTime;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public java.time.LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    }

    /**
     * 情感分析结果
     */
    class SentimentAnalysis {
        private String sentiment; // positive, negative, neutral
        private Double confidence;
        private Double score; // -1.0到1.0

        public String getSentiment() { return sentiment; }
        public void setSentiment(String sentiment) { this.sentiment = sentiment; }

        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }

    /**
     * 意图识别结果
     */
    class IntentRecognition {
        private String intent; // order_inquiry, price_inquiry, shipping_inquiry, etc.
        private Double confidence;
        private java.util.List<String> entities;

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }

        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }

        public java.util.List<String> getEntities() { return entities; }
        public void setEntities(java.util.List<String> entities) { this.entities = entities; }
    }

    /**
     * 知识条目
     */
    class Knowledge {
        private Long id;
        private String category;
        private String question;
        private String answer;
        private String language;
        private java.util.List<String> tags;
        private Integer priority;
        private Boolean active;
        /**
         * 📌 **检索命中的相关度**（0.0 ~ 1.0）。
         *
         * <p>⚠️ **为什么要有这个独立字段**（2026-11-04 修）：
         * 旧实现把相关度**塞回 {@code priority}**（{@code priority = (int)(similarity*100)}）
         * → {@code priority} 这个字段在搜索结果里**含义完全变了**：
         * 用户自己设的优先级被冲掉，接口返回的 {@code priority} 其实是相关度×100。
         * <p>→ 对外暴露的 {@code GET /knowledge/search} 会返回**语义错乱的 priority**，
         * 调用方按「优先级」读它就是读到了垃圾。
         * <p>→ 现在：{@code priority} **保持原义**（用户设的优先级），
         * 相关度单独放 {@link #similarity}。
         *
         * <p>📌 只在检索结果里有值；新增知识时为 null（不落库）。
         */
        private Double similarity;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }

        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public java.util.List<String> getTags() { return tags; }
        public void setTags(java.util.List<String> tags) { this.tags = tags; }

        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }

        public Double getSimilarity() { return similarity; }
        public void setSimilarity(Double similarity) { this.similarity = similarity; }
    }
}
