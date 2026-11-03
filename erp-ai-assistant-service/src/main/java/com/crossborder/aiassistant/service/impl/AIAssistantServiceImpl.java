package com.crossborder.aiassistant.service.impl;

import jakarta.annotation.PostConstruct;
import com.crossborder.aiassistant.config.LLMConfig;
import com.crossborder.aiassistant.dto.ChatRequest;
import com.crossborder.aiassistant.dto.ChatResponse;
import com.crossborder.aiassistant.dto.ServiceStatistics;
import com.crossborder.aiassistant.entity.KnowledgeBase;
import com.crossborder.aiassistant.repository.KnowledgeBaseRepository;
import com.crossborder.aiassistant.service.AIAssistantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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

    /**
     * 知识库仓储（表 knowledge_base）
     *
     * 📌 原来是 {@code private final List<Knowledge> knowledgeBase = new ArrayList<>();}，
     *    注释直接写着「模拟知识库」。后果：
     *    · 数据库 knowledge_base 表里的种子数据（6 条）**从来没人读**；
     *    · 通过接口新增的知识**重启就丢**；
     *    · 前端没有管理入口 → 知识库永远只有代码里硬编码的 4 条。
     *    现在改成读写真实表，聊天检索和前端管理用的是同一份数据。
     */
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private LLMConfig llmConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    // 服务统计
    private final ServiceStatistics statistics = new ServiceStatistics();
    private long responseTimeSum = 0;
    private long responseTimeCount = 0;

    /**
     * 初始化知识库
     *
     * ⚠️ 必须用 @PostConstruct 而不是构造函数：
     *    构造函数执行时 @Autowired 的仓储还没注入，
     *    查表必然 NPE（而且异常发生在 bean 创建阶段，整个服务起不来）。
     */
    @PostConstruct
    public void initKnowledgeBase() {
        log.info("初始化知识库...");

        // 📌 只在表里**一条都没有**时才灌默认知识。
        //    否则每次重启都会把 4 条示例知识重复插一遍。
        long existing = knowledgeBaseRepository.count();
        if (existing > 0) {
            log.info("知识库已有 {} 条记录，跳过默认知识初始化", existing);
            return;
        }

        // 添加示例知识（仅当表为空时）
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
        addKnowledge(k3);

        Knowledge k4 = new Knowledge();
        k4.setCategory("退货");
        k4.setQuestion("如何申请退货？");
        k4.setAnswer("您可以在订单详情页申请退货退款，填写退货原因后，我们会安排快递上门取件。");
        k4.setLanguage("zh");
        k4.setTags(Arrays.asList("退货", "退款", "售后"));
        addKnowledge(k4);

        log.info("知识库初始化完成 - 知识条目数: {}", knowledgeBaseRepository.count());
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        log.info("AI对话 - 用户ID: {}, 会话ID: {}, 消息: {}", 
                request.getUserId(), request.getSessionId(), request.getMessage());

        // 记录对话开始
        recordConversationStart();

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
                recordRAGRetrieval();
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
            int promptTokens = prompt.length() / 4;
            int completionTokens = aiResponse.length() / 4;
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(promptTokens + completionTokens);
            response.setUsage(usage);
            recordTokens(usage.getTotalTokens());

            // 6. 响应时间
            long responseTime = System.currentTimeMillis() - startTime;
            response.setResponseTime(responseTime);

            // 记录成功
            recordSuccess(responseTime);

            log.info("AI对话完成 - 响应时间: {}ms", responseTime);

        } catch (Exception e) {
            log.error("AI对话失败", e);
            response.setContent("抱歉，我现在无法回答这个问题。请稍后再试。");
            response.setError(e.getMessage());
            recordFailure(e.getMessage());
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
        List<String> positiveWords = Arrays.asList("好", "棒", "优秀", "满意", "喜欢",
                "good", "excellent", "satisfied", "like", "love");
        List<String> negativeWords = Arrays.asList("差", "糟糕", "不满", "讨厌", "失望",
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

        // 📌 原来只是往内存 List 里塞 —— 重启就没了，
        //    而且和数据库 knowledge_base 表没有任何关系。
        //    现在真正落库，前端管理界面和聊天检索共用这一份数据。
        KnowledgeBase entity = KnowledgeBase.builder()
                .category(knowledge.getCategory())
                .question(knowledge.getQuestion())
                .answer(knowledge.getAnswer())
                .language(knowledge.getLanguage() == null ? "zh" : knowledge.getLanguage())
                .tags(knowledge.getTags())
                .priority(knowledge.getPriority() == null ? 0 : knowledge.getPriority())
                .viewCount(0)
                .helpfulCount(0)
                .active(true)
                .build();
        KnowledgeBase saved = knowledgeBaseRepository.save(entity);

        knowledge.setId(saved.getId());
        knowledge.setActive(true);

        log.info("知识添加成功: id={}", saved.getId());
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

        // 📌 从真实表读（原来读的是内存 List，重启即空）
        List<KnowledgeBase> stored = knowledgeBaseRepository.findByActiveTrue();
        for (KnowledgeBase row : stored) {
            Knowledge knowledge = toKnowledge(row);
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
     * 调用LLM API
     * 优先使用真实API（当配置了API Key时），否则使用模拟回复
     */
    private String callLLMAPI(String prompt, ChatRequest request) {
        // 检查是否配置了真实的LLM API
        if (llmConfig.isConfigured()) {
            try {
                log.info("调用真实LLM API - Provider: {}, Model: {}", 
                        llmConfig.getProvider(), llmConfig.getModel());
                return callRealLLMAPI(prompt, request);
            } catch (Exception e) {
                log.error("LLM API调用失败， fallback到模拟回复", e);
                return callMockLLMAPI(request);
            }
        } else {
            log.info("未配置LLM API，使用模拟回复");
            return callMockLLMAPI(request);
        }
    }

    /**
     * 调用真实LLM API（DeepSeek/OpenAI/Azure OpenAI）
     */
    private String callRealLLMAPI(String prompt, ChatRequest request) {
        String apiUrl = llmConfig.getBaseUrl() + "/v1/chat/completions";
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", getSystemPrompt(request)));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);
        
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", llmConfig.getTemperature());
        params.put("max_tokens", llmConfig.getMaxTokens());
        requestBody.putAll(params);
        
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmConfig.getApiKey());
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            
            if (response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            throw new RuntimeException("LLM API响应格式错误");
        } catch (Exception e) {
            log.error("LLM API调用失败: {}", e.getMessage());
            throw new RuntimeException("LLM API调用失败", e);
        }
    }

    /**
     * 获取系统提示词
     */
    private String getSystemPrompt(ChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是OmniTrade跨境电商ERP系统的AI客服助手火球鼠。\n");
        prompt.append("你的职责是帮助用户解决关于订单管理、商品管理、库存管理、财务管理等问题。\n");
        prompt.append("请用").append(request.getLanguage() != null ? request.getLanguage() : "中文").append("回复。\n");
        prompt.append("回复要简洁、专业、有帮助。\n");
        prompt.append("如果用户问的问题与ERP系统无关，请礼貌地引导回主题。\n");
        return prompt.toString();
    }

    /**
     * 模拟LLM回复（当未配置API Key时使用）
     */
    private String callMockLLMAPI(ChatRequest request) {
        String message = request.getMessage().toLowerCase();

        if (message.contains("订单") || message.contains("order")) {
            return "您可以在「我的订单」页面查看所有订单信息。如果需要帮助，请提供订单号，我会帮您查询详细状态。";
        } else if (message.contains("价格") || message.contains("price")) {
            return "产品价格会根据市场情况动态调整。您可以在产品详情页查看当前价格和历史价格趋势。";
        } else if (message.contains("物流") || message.contains("shipping") || message.contains("快递")) {
            return "订单发货后，您可以在订单详情页查看物流信息。我们会提供物流单号和实时追踪链接。";
        } else if (message.contains("库存") || message.contains("stock")) {
            return "我们的库存系统有智能预测功能，会根据历史销量自动生成补货建议。库存预警会提前通知您。";
        } else if (message.contains("退款") || message.contains("退货")) {
            return "退款退货可以在订单详情页申请，财务部门会在1-3个工作日内处理完成。";
        } else if (message.contains("你好") || message.contains("hello") || message.contains("hi")) {
            return "您好！我是火球鼠🔥 您的AI助手，很高兴为您服务！请问有什么可以帮到您的？";
        } else if (message.contains("谢谢") || message.contains("感谢")) {
            return "不客气！很高兴能帮到您！如有其他问题随时问我哦～";
        } else if (message.contains("再见") || message.contains("拜拜")) {
            return "再见！祝您生意兴隆！🔥 有需要随时找我！";
        } else {
            return "收到您的问题！我是基于OmniTrade ERP v1.5.0的AI助手，我可以帮您解答关于订单、库存、价格、物流等问题。请告诉我具体想了解什么？";
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

    /**
     * 实体 → 接口内的 Knowledge
     */
    private Knowledge toKnowledge(KnowledgeBase row) {
        Knowledge k = new Knowledge();
        k.setId(row.getId());
        k.setCategory(row.getCategory());
        k.setQuestion(row.getQuestion());
        k.setAnswer(row.getAnswer());
        k.setLanguage(row.getLanguage());
        k.setTags(row.getTags());
        k.setPriority(row.getPriority());
        k.setActive(row.getActive() == null || row.getActive());
        return k;
    }

    // ========== 统计方法 ==========

    @Override
    public ServiceStatistics getStatistics() {
        log.info("获取服务统计信息");

        statistics.setTotalConversations(statistics.getTotalConversations());
        statistics.setKnowledgeBaseSize(knowledgeBaseRepository.countByActiveTrue());
        statistics.setActiveSessions(sessionHistory.size());

        // 计算平均响应时间
        if (responseTimeCount > 0) {
            statistics.setAvgResponseTime((double) responseTimeSum / responseTimeCount);
        }

        // 统计活跃用户
        Set<Long> uniqueUsers = new HashSet<>();
        for (List<ChatMessage> messages : sessionHistory.values()) {
            for (ChatMessage msg : messages) {
                if (msg.getUserId() != null) {
                    uniqueUsers.add(msg.getUserId());
                }
            }
        }
        statistics.setActiveUsers(uniqueUsers.size());

        statistics.setStatisticsTime(LocalDateTime.now());

        return statistics;
    }

    @Override
    public void resetStatistics() {
        log.info("重置统计数据");
        statistics.setTotalConversations(0);
        statistics.setSuccessfulConversations(0);
        statistics.setFailedConversations(0);
        statistics.setLlmApiCalls(0);
        statistics.setMockResponses(0);
        statistics.setTotalTokens(0);
        statistics.setAvgResponseTime(0);
        statistics.setMaxResponseTime(0);
        statistics.setMinResponseTime(0);
        statistics.setRagRetrievals(0);
        statistics.getRecentErrors().clear();
        responseTimeSum = 0;
        responseTimeCount = 0;
    }

    /**
     * 记录对话开始
     */
    private void recordConversationStart() {
        statistics.setTotalConversations(statistics.getTotalConversations() + 1);
    }

    /**
     * 记录成功响应
     */
    private void recordSuccess(long responseTime) {
        statistics.setSuccessfulConversations(statistics.getSuccessfulConversations() + 1);
        responseTimeSum += responseTime;
        responseTimeCount++;

        if (responseTime > statistics.getMaxResponseTime()) {
            statistics.setMaxResponseTime(responseTime);
        }
        if (statistics.getMinResponseTime() == 0 || responseTime < statistics.getMinResponseTime()) {
            statistics.setMinResponseTime(responseTime);
        }
    }

    /**
     * 记录失败
     */
    private void recordFailure(String error) {
        statistics.setFailedConversations(statistics.getFailedConversations() + 1);
        if (statistics.getRecentErrors().size() < 10) {
            statistics.getRecentErrors().add(LocalDateTime.now() + ": " + error);
        }
    }

    /**
     * 记录LLM API调用
     */
    private void recordLLMCall(boolean success) {
        statistics.setLlmApiCalls(statistics.getLlmApiCalls() + 1);
        if (!success) {
            statistics.setMockResponses(statistics.getMockResponses() + 1);
        }
    }

    /**
     * 记录Token使用
     */
    private void recordTokens(int tokens) {
        statistics.setTotalTokens(statistics.getTotalTokens() + tokens);
    }

    /**
     * 记录RAG检索
     */
    private void recordRAGRetrieval() {
        statistics.setRagRetrievals(statistics.getRagRetrievals() + 1);
    }
}
