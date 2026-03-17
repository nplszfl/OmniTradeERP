package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.dto.ChatRequest;
import com.crossborder.aiassistant.dto.ChatResponse;

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
     * 识别意图
     *
     * @param text 用户输入
     * @return 意图识别结果
     */
    IntentRecognition recognizeIntent(String text);

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

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getCategory() { return category; }
        public void setCategory(String) category) { this.category = category; }

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
    }
}
