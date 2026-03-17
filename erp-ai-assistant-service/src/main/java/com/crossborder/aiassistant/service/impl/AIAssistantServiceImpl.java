package com.crossborder.aiassistant.service.impl;

import com.crossborder.aiassistant.dto.ChatRequest;
import com.crossborder.aiassistant.dto.ChatResponse;
import com.crossborder.aiassistant.service.AIAssistantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI智能客服服务实现
 *
 * 集成LLM API和RAG检索，提供智能问答能力
 */
@Slf4j
@Service
public class AIAssistantServiceImpl implements AIAssistantService {

    // 模拟对话历史存储
    private final Map<String, List<ChatMessage>> sessionHistory = new ConcurrentHashMap<>();

    // 模拟知识库
    private final List<Knowledge> knowledgeBase = new ArrayList<>();

    public AIAssistantServiceImpl() {
        // 初始化知识库
        initKnowledgeBase();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        log.info("AI对话 - 用户ID: {}, 会话ID: {}, 消息: {}", 
                request.getUserId(), request.getSessionId(), request.getMessage());

        long startTime = System.currentTimeMillis();

        ChatResponse response = new ChatResponse();
        response.setResponseId(UUID.randomUUID().toString());
        response.setSessionId(request.getSessionId() != null ? 
                request.getSessionId() : UUID.randomUUID().toString());
        response.setLanguage(detectLanguage(request.getMessage()));
        response.setModel(request.getModel() != null ? request.getModel() : "deepseek-chat");
        response.setCreateTime(LocalDateTime.now());

        try {
            // 1. 检索相关知识（RAG）
            List<ChatResponse.KnowledgeChunk> retrievedChunks = new ArrayList<>();
            if (Boolean.TRUE.equals(request.getUseRAG())) {
                retrievedChunks = retrieveKnowledge(request.getMessage(), 3);
                response.setRetrievedChunks(retrievedChunks);
            }

            // 2. 构建提示词
            String prompt = buildPrompt(request, retrievedChunks);

            // 3. 调用LLM API
            String aiResponse = callLLMAPI(prompt, request);
            response.setContent(aiResponse);

            // 4. 记录对话历史
            saveChatMessage(request.getUserId(), response.getSessionId(), 
                    "user", request.getMessage(), response.getLanguage());
            saveChatMessage(request.getUserId(), response.getSessionId(), 
                    "assistant", aiResponse, response.getLanguage());

            // 5. Token使用统计
            ChatResponse.TokenUsage usage = new ChatResponse.TokenUsage();
            usage.setPromptTokens(prompt.length() / 4); // 估算
            usage.setCompletionTokens(aiResponse.length() / 4);
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
            response.setUsage(usage);

            // 6. 响应时间
            response.setResponseTime(System.currentTimeMillis() - startTime);

            log.info("AI对话完成 - 响应时间: {}ms", response.getResponseTime());

        } catch (Exception e) {
            log.error("AI对话失败", e);
            response.setContent("抱歉，我现在无法回答这个问题。请稍后再试。");
            response.setError(e.getMessage());
        }

        return response;
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        log.info("AI流式对话 - 用户ID: {}", request.getUserId());

        return Flux.create(emitter -> {
            try {
                String prompt = buildPrompt(request, Collections.emptyList());
                String response = callLLMAPI(prompt, request);

                // 模拟流式输出
                for (int i = 0; i < response.length(); i++) {
                    String chunk = response.substring(i, i + 1);
                    emitter.next(chunk);
                    Thread.sleep(50); // 模拟延迟
                }

                emitter.complete();

                log.info("AI流式对话完成");

            } catch (Exception e) {
                log.error("AI流式对话失败", e);
                emitter.error(e);
            }
        });
    }

    @Override
    public List<ChatMessage> getChatHistory(Long userId, String sessionId) {
        log.info("获取对话历史 - 用户ID: {}, 会话ID: {}", userId, sessionId);

        String key = sessionId + ":" + userId;
        List<ChatMessage> history = sessionHistory.getOrDefault(key, new ArrayList<>());

        // 按时间排序（最新的在前）
        history.sort(Comparator.comparing(ChatMessage::getCreateTime).reversed());

        return history;
    }

    @Override
    public void clearChatHistory(Long userId, String sessionId) {
        log.info("清空对话历史 - 用户ID: {}, 会话ID: {}", userId, sessionId);

        String key = sessionId + ":" + userId;
        sessionHistory.remove(key);

        log.info("对话历史已清空");
    }

    @Override
    public SentimentAnalysis analyzeSentiment(String text) {
        log.info("分析情感 - 文本:长度 {}", text.length());

        SentimentAnalysis analysis = new SentimentAnalysis();

        // 简化版情感分析（实际应该使用NLP模型）
        List positiveWords = Arrays.asList("好", "棒", "优秀", "满意", "喜欢", 
                "good", "excellent", "satisfied", "like", "love");
        List negativeWords = Arrays.asList("差", "糟糕", "不满", "讨厌", "失望",
                "bad", "terrible", "dissatisfied", "hate", "disappointed");

        double score = 0;
        for (String word : positiveWords) {
            if (text.toLowerCase().contains(word)) {
                score += 0.1;
            }
        }
        for (String word : negativeWords) {
            if (text.toLowerCase().contains(word)) {
                score -= 0.1;
            }
        }

        // 归一化到[-1, 1]
        score = Math.max(-1.0, Math.min(1.0, score));

        if (score > 0.1) {
            analysis.setSentiment("positive");
        } else if (score < -0.1) {
            analysis.setSentiment("negative");
        } else {
            analysis.setSentiment("neutral");
        }

        analysis.setScore(score);
        analysis.setConfidence(Math.abs(score));

        log.info("情感分析完成 - 情感: {}, 分数: {}", 
                analysis.getSentiment(), analysis.getScore());

        return analysis;
    }

    @Override
    public IntentRecognition recognizeIntent(String text) {
        log.info("识别意图 - 文本: {}", text);

        IntentRecognition recognition = new IntentRecognition();

        // 简化版意图识别（实际应该使用NLP模型）
        Map<String, List<String>> intentPatterns = new HashMap<>();
        intentPatterns.put("order_inquiry", Arrays.asList("订单", "order", "查询订单", "我的订单"));
        intentPatterns.put("price_inquiry", Arrays.asList("价格", "price", "多少钱", "费用"));
        intentPatterns.put("shipping_inquiry", Arrays.asList("物流", "shipping", "快递", "配送"));
        intentPatterns.put("product_inquiry", Arrays.asList("产品", "product", "商品", "物品"));
        intentPatterns.put("product_inquiry", Arrays.asList("退货", "return", "退款", "refund"));

        String bestIntent = "general_inquiry";
        double bestScore = 0;

        for (Map.Entry<String, List<String>> entry : intentPatterns.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (text.toLowerCase().contains(pattern.toLowerCase())) {
                    double score = 1.0 / (entry.getValue().size());
                    if (score > bestScore) {
                        bestScore = score;
                        bestIntent = entry.getKey();
                    }
                }
            }
        }

        recognition.setIntent(bestIntent);
        recognition.setConfidence(bestScore);
        recognition.setEntities(extractEntities(text));

        log.info("意图识别完成 - 意图: {}, 置信度: {}", 
                recognition.getIntent(), recognition.getConfidence());

        return recognition;
    }

    @Override
    public void addKnowledge(Knowledge knowledge) {
        log.info("添加知识 - 分类: {}, 问题: {}", knowledge.getCategory(), knowledge.getQuestion());

        knowledge.setId(System.currentTimeMillis());
        knowledge.setActive(true);

        knowledgeBase.add(knowledge);

        log.info("知识添加成功");
    }

    @Override
    public void batchAddKnowledge(List<Knowledge> knowledges) {
        log.info("批量添加知识 - 数量: {}", knowledges.size());

        for (Knowledge knowledge : knowledges) {
            addKnowledge(knowledge);
        }

        log.info("批量知识添加成功");
    }

    @Override
    public List<Knowledge> searchKnowledge(String query, int topK) {
        log.info("检索知识库 - 查询: {}, TopK: {}", query, topK);

        // 简化版检索（实际应该使用向量数据库）
        List<Knowledge> results = new ArrayList<>();

        for (Knowledge knowledge : knowledgeBase) {
            if (!knowledge.getActive()) {
                continue;
            }

            // 简单的关键词匹配
            double similarity = calculateSimilarity(query, knowledge.getQuestion());
            if (similarity > 0.3) { // 相似度阈值
                knowledge.setPriority((int) (similarity * 100));
                results.add(knowledge);
            }
        }

        // 按相似度排序
        results.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        // 返回TopK
        return results.stream().limit(topK).collect(Collectors.toList());
    }

    // ========== 私有方法 ==========

    /**
     * 初始化知识库
     */
    private void initKnowledgeBase() {
        log.info("初始化知识库...");

        // 添加示例知识
        Knowledge k1 = new Knowledge();
        k1.setCategory("订单");
        k1.setQuestion("如何查询我的订单？");
        k1.setAnswer("您可以在「我的订单」页面查看所有订单信息，包括待付款、待发货、已发货等状态。");
        k1.setLanguage("zh");
        k1.setTags(Arrays.asList("订单", "查询"));
        addKnowledge(k1);

        Knowledge k2 = new Knowledge();
        k2.setCategory("物流");
        k2.setQuestion("订单发货后多久能收到？");
        k2.setAnswer("一般情况下，国内订单1-3天，国际订单7-15天。具体时效取决于物流方式。");
        k2.setLanguage("zh");
        k2.setTags(Arrays.asList("物流", "时效"));
        addKnowledge(k2);

        Knowledge k3 = new Knowledge();
        k3.setCategory("产品");
        k3.setQuestion("产品有质量问题怎么办？");
        k3.setAnswer("如果产品有质量问题，请在收到货7天内申请售后，我们会安排退换货。");
        k3.setLanguage("zh");
        k3.setTags(Arrays.asList("售后", "质量"));
        addKnowledge(k4);

        log.info("知识库初始化完成 - 知识条目数: {}", knowledgeBase.size());
    }

    /**
     * 检测语言
     */
    private String detectLanguage(String text) {
        // 简化版：检测中文字符
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) {
                return "zh";
            }
        }
        return "en";
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(ChatRequest request, List<ChatResponse.KnowledgeChunk> retrievedChunks) {
        StringBuilder prompt = new StringBuilder();

        // 系统提示
        prompt.append("你是一个专业的跨境电商ERP系统智能助手。");
        prompt.append("你的职责是帮助用户解决问题，提供准确的信息和建议。\n\n");

        // 知识库信息
        if (!retrievedChunks.isEmpty()) {
            prompt.append("相关知识：\n");
            for (ChatResponse.KnowledgeChunk chunk : retrievedChunks) {
                prompt.append("- ").append(chunk.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        // 对话历史
        String key = request.getSessionId() + ":" + request.getUserId();
        List<ChatMessage> history = sessionHistory.getOrDefault(key, new ArrayList<>());
        if (history.size() > 2) { // 只取最近2轮对话
            prompt.append("对话历史：\n");
            for (ChatMessage msg : history.subList(history.size() - 2, history.size())) {
                prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        // 用户问题
        prompt.append("用户问题: ").append(request.getMessage());

        return prompt.toString();
    }

    /**
     * 调用LLM API（模拟）
     */
    private String callLLMAPI(String prompt, ChatRequest request) {
        // TODO: 实际应该调用OpenAI/DeepSeek API
        // 这里使用模拟的智能回复

        String message = request.getMessage().toLowerCase();

        // 模拟智能回复
        if (message.contains("订单") || message.contains("order")) {
            return "您可以在「我的订单」页面查看所有订单信息。如果需要帮助，请提供订单号，我会帮您查询详细状态。";
        } else if (message.contains("价格") || message.contains("price")) {
            return "产品价格会根据市场情况动态调整。您可以在产品详情页查看当前价格和历史价格趋势。";
        } else if (message.contains("物流") || message.contains("shipping")) {
            return "订单发货后，您可以在订单详情页查看物流信息。我们会提供物流单号和实时追踪链接。";
        } else if (message.contains("库存") || message.contains("stock")) {
            return "我们的库存系统会自动预测销量并生成补货建议。大部分商品都有充足的库存保障。";
        } else if (message.contains("你好") || message.contains("hello")) {
            return "您好！我是火球鼠AI助手，很高兴为您服务。请问有什么我可以帮助您的？";
        } else {
            return "收到您的问题，我正在处理中。这是一个基于v1.5.0 AI优先战略的智能ERP系统，我会尽力为您提供最好的服务。";
        }
    }

    /**
     * 保存对话消息
     */
    private void saveChatMessage(Long userId, String sessionId, String role, 
                               String content, String language) {
        ChatMessage message = new ChatMessage();
        message.setId(System.currentTimeMillis());
        message.setUserId(userId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setLanguage(language);
        message.setCreateTime(LocalDateTime.now());

        String key = sessionId + ":" + userId;
        sessionHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
    }

    /**
     * 检索相关知识
     */
    private List<ChatResponse.KnowledgeChunk> retrieveKnowledge(String query, int topK) {
        List<Knowledge> knowledges = searchKnowledge(query, topK);

        return knowledges.stream()
                .map(k -> {
                    ChatResponse.KnowledgeChunk chunk = new ChatResponse.KnowledgeChunk();
                    chunk.setChunkId(String.valueOf(k.getId()));
                    chunk.setContent(k.getAnswer());
                    chunk.setSimilarity(k.getPriority() / 100.0);
                    chunk.setSource(k.getCategory());
                    chunk.setCategory(k.getCategory());
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算相似度（简化版）
     */
    private double calculateSimilarity(String query, String text) {
        Set<String> queryWords = new HashSet<>(Arrays.asList(query.toLowerCase().split("\\s+")));
        Set<String> textWords = new HashSet<>(Arrays.asList(text.toLowerCase().split("\\s+")));

        Set<String> intersection = new HashSet<>(queryWords);
        intersection.retainAll(textWords);

        Set<String> union = new HashSet<>(queryWords);
        union.addAll(textWords);

        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    /**
     * 提取实体（简化版）
     */
    private List<String> extractEntities(String text) {
        List<String> entities = new ArrayList<>();

        // 提取订单号（简化版）
        if (text.matches(".*\\d{10,}.*")) {
            entities.add("order_number");
        }

        // 提取产品编号（简化版）
        if (text.matches(".*PROD-\\d+.*")) {
            entities.add("product_id");
        }

        return entities;
    }
}
