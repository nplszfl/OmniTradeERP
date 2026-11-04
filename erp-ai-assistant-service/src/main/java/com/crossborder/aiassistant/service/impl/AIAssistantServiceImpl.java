package com.crossborder.aiassistant.service.impl;

import jakarta.annotation.PostConstruct;
import com.crossborder.aiassistant.config.LLMConfig;
import com.crossborder.aiassistant.dto.ChatRequest;
import com.crossborder.aiassistant.dto.ChatResponse;
import com.crossborder.aiassistant.dto.ServiceStatistics;
import com.crossborder.aiassistant.entity.KnowledgeBase;
import com.crossborder.aiassistant.repository.KnowledgeBaseRepository;
import com.crossborder.aiassistant.service.AIAssistantService;
import com.crossborder.erp.llm.LLMEndpointResolver;
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

    /**
     * 模拟回复时写在 {@code model} 字段里的标记值
     *
     * <p>📌 <b>不能继续写 "deepseek-chat"</b> —— 那是在<b>谎报事实</b>
     * （说这次回答是 deepseek-chat 产生的，实际是一段固定模板）。
     */
    private static final String MOCK_MODEL_NAME = "mock";

    /**
     * 拉丁词（连续英文字母）—— 用于在相似度计算里把英文单独切词。
     * 📌 中文没有空格，必须靠字符 n-gram；英文靠词根，切成 bigram 会误匹配。
     */
    private static final java.util.regex.Pattern LATIN_WORD =
            java.util.regex.Pattern.compile("[a-z]+");

    /**
     * 相似度阈值。
     *
     * 📌 **不能拿 0.3**：字符 n-gram 下「4 字查询」对「9 字问题」的 Dice 上限
     *    大约只有 0.17（算上同义词展开才勉强到 0.2 上下）→ 真实问法全部落空。
     * 📌 **也不能调太低**：会引入误召回（「今天天气怎么样」这种无关问法）。
     *    0.2 是实测下来「真实问法能召回 + 无关问法不召回」的平衡点。
     */
    private static final double SIMILARITY_THRESHOLD = 0.2;

    /**
     * 包含度的权重（包含度 = |A∩B| / min(|A|,|B|)）。
     *
     * 📌 乘 0.9 而不是 1.0：包含度天然偏高（短查询只要命中就接近 1），
     *    留一点折扣，避免「只命中一个词」就压过「整体很像」的那条。
     */
    private static final double CONTAINMENT_WEIGHT = 0.9;

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
        response.setModel(request.getModel() != null ? request.getModel() : MOCK_MODEL_NAME);
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
            LLMAnswer answer = callLLMAPI(prompt, request);
            response.setContent(answer.content());
            response.setMock(answer.mock());
            if (answer.mock()) {
                // 📌 **降级必须可被看见** —— 之前这里静默返回一段 canned 文本，
                //    而 model 仍写着 deepseek-chat → 调用方完全无法分辨真假
                response.setMockReason(answer.reason());
                response.setModel(MOCK_MODEL_NAME);
                log.warn("AI对话返回的是**模拟回复**（mock=true）：{}", answer.reason());
            } else {
                // 📌 真实调用成功 → model 写**真实**模型名
                response.setModel(llmConfig != null && llmConfig.getModel() != null
                        ? llmConfig.getModel() : response.getModel());
            }

            // 4. 记录对话历史
            saveChatMessage(request.getUserId(), response.getSessionId(),
                    "user", request.getMessage(), response.getLanguage());
            saveChatMessage(request.getUserId(), response.getSessionId(),
                    "assistant", answer.content(), response.getLanguage());

            // 5. Token使用统计
            // 📌 **只对真实调用统计** —— 之前对模拟回复也按 `长度/4` 编造 token 数
            //    → 那是个**假事实**（用户以为真的调用了 LLM 并消耗了 token）
            if (!answer.mock()) {
                ChatResponse.TokenUsage usage = new ChatResponse.TokenUsage();
                int promptTokens = prompt.length() / 4;
                int completionTokens = answer.content().length() / 4;
                usage.setPromptTokens(promptTokens);
                usage.setCompletionTokens(completionTokens);
                usage.setTotalTokens(promptTokens + completionTokens);
                response.setUsage(usage);
                recordTokens(usage.getTotalTokens());
            }

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
                LLMAnswer answer = callLLMAPI(prompt, request);
                String response = answer.content();
                if (answer.mock()) {
                    // 📌 流式也要说真话 —— 前面先推一条说明，别让调用方以为这是真 AI
                    emitter.next("[" + answer.reason() + "，以下为模拟回复]\n");
                }

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
            // 📌 **要匹配多个字段，不能只匹配 question**（2026-11-04 实测）：
            //   用户问「退钱流程是什么」→ question「如何申请退款？」一个共同字都没有，
            //   但**标签**是「退款」→ 命中标签才能召回。
            //   → FAQ 检索应该把 问题/答案/分类/标签 一起参与匹配，取最高分。
            double similarity = Math.max(
                    calculateSimilarity(query, knowledge.getQuestion()),
                    Math.max(
                            calculateSimilarity(query, knowledge.getAnswer()),
                            Math.max(
                                    calculateSimilarity(query, knowledge.getCategory()),
                                    tagsSimilarity(query, knowledge.getTags()))));
            if (similarity > SIMILARITY_THRESHOLD) { // 相似度阈值
                // 📌 **不要再把相关度塞回 priority**（2026-11-04 修）：
                //   旧写法 `knowledge.setPriority((int)(similarity * 100))`
                //   → priority 在搜索结果里**含义完全变了**（用户设的优先级被冲掉），
                //   对外的 `GET /knowledge/search` 返回的 priority 其实是相关度×100
                //   → 调用方按「优先级」读它就是读到了垃圾。
                //   → 相关度单独放 `similarity` 字段，priority 保持原义。
                knowledge.setSimilarity(similarity);
                results.add(knowledge);
            }
        }

        // 按相关度排序（**不再借用 priority**）
        results.sort((a, b) -> Double.compare(
                b.getSimilarity() == null ? 0.0 : b.getSimilarity(),
                a.getSimilarity() == null ? 0.0 : a.getSimilarity()));

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
     *
     * <p>📌 <b>降级必须带上原因</b>（2026-11-04 实测）：
     * 之前真实调用失败（404/超时/鉴权错）→ catch → <b>静默</b>返回一段 canned 文本，
     * 响应里 {@code model} 还写着 {@code deepseek-chat} →
     * <b>调用方无法分辨真假</b> → AI 从来没真的工作过，但界面看着完全正常。
     */
    private LLMAnswer callLLMAPI(String prompt, ChatRequest request) {
        // 检查是否配置了真实的LLM API
        if (llmConfig.isConfigured()) {
            try {
                log.info("调用真实LLM API - Provider: {}, Model: {}",
                        llmConfig.getProvider(), llmConfig.getModel());
                return LLMAnswer.real(callRealLLMAPI(prompt, request));
            } catch (Exception e) {
                // 📌 降级不是「静默失败」—— 原因要能传到调用方
                log.error("LLM API调用失败， fallback到模拟回复", e);
                return LLMAnswer.mock(callMockLLMAPI(request),
                        "真实 API 调用失败：" + e.getMessage());
            }
        } else {
            log.info("未配置LLM API，使用模拟回复");
            return LLMAnswer.mock(callMockLLMAPI(request), "未配置 LLM API Key 或 llm.enabled=false");
        }
    }

    /**
     * 一次 LLM 调用的结果：**内容 + 是不是模拟的 + 降级原因**
     *
     * <p>📌 为什么要单独一个类型：
     * 之前 {@code callLLMAPI} 直接返回 {@code String}，
     * 「这是不是模拟的」这个信息<b>在方法签名里就丢了</b> →
     * 调用方<b>想报都报不出来</b>。
     *
     * @param content 回复内容
     * @param mock    true = 模拟回复（没配 key，或真实调用失败降级）
     * @param reason  降级原因（mock=true 时有值）
     */
    public record LLMAnswer(String content, boolean mock, String reason) {

        public static LLMAnswer real(String content) {
            return new LLMAnswer(content, false, null);
        }

        public static LLMAnswer mock(String content, String reason) {
            return new LLMAnswer(content, true, reason);
        }
    }

    /**
     * 调用真实LLM API（DeepSeek/OpenAI/Azure OpenAI）
     */
    private String callRealLLMAPI(String prompt, ChatRequest request) {
        // 📌 **端点拼接的唯一事实来源 = LLMEndpointResolver**
        // 📌 之前这里写死 `baseUrl + "/v1/chat/completions"`，
        //    而 application.yml 的 base-url 默认带 /v1 → 拼出 **双 /v1** → 必然 404
        //    → catch 之后静默降级成模拟回复 → **AI 从来没真的调通过**
        String apiUrl = LLMEndpointResolver.chatCompletionsUrl(
                llmConfig.getBaseUrl(),
                llmConfig.getProvider(),
                llmConfig.getDeployment(),
                llmConfig.getApiVersion());
        
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
        // 📌 Azure 用 `api-key` 头，其他家（OpenAI 兼容协议）用 `Authorization: Bearer`
        if (LLMEndpointResolver.isAzure(llmConfig.getProvider())) {
            headers.set("api-key", llmConfig.getApiKey());
        } else {
            headers.setBearerAuth(llmConfig.getApiKey());
        }
        
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
                    // 📌 **相关度取自 similarity 字段**（2026-11-04 修）：
                    //   旧写法 `k.getPriority() / 100.0` 是**依赖旧的那个 bug**
                    //   （相关度被塞回 priority）才能算对 ——
                    //   一旦修正 priority 的语义，这里就静默算错了。
                    //   → 直接读 similarity，两边含义一致。
                    chunk.setSimilarity(k.getSimilarity() == null ? 0.0 : k.getSimilarity());
                    chunk.setSource(k.getCategory());
                    chunk.setCategory(k.getCategory());
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    /**
     * **低信息量 token**（停用词）—— 匹配时要忽略。
     *
     * <p>📌 **实测踩到的坑**（2026-11-04）：
     * 「如何退款」和「如何查询我的订单？」**都含「如何」** →
     * 包含度 = 1/3 = 0.33 → **退款问题被订单知识抢走**（排第一的居然是错的）。
     *
     * <p>「如何」「怎么」「什么」这类**疑问句式**不含任何领域信息，
     * 却因为是两个字的 bigram 而在短查询里占很大权重 → 必须滤掉。
     *
     * <p>📌 用**显式清单**而不是「按组成字符判断」——
     * 否则「查询」会因为含「询」被误杀（查+询），「退货」会因为含「退」…
     * 显式清单只杀真正没信息量的那些。
     */
    private static final Set<String> STOPWORD_TOKENS = Set.copyOf(List.of(
            "如何",
            "怎么",
            "怎办",
            "咋办",
            "什么",
            "哪些",
            "哪种",
            "哪样",
            "是否",
            "可以",
            "能否",
            "需要",
            "请问",
            "咨询",
            "一下",
            "我想",
            "我的",
            "您的",
            "这个",
            "那个",
            "一个",
            "一次",
            "为什么",
            "怎样",
            "哪里",
            "哪儿",
            "的话",
            "而且",
            "但是",
            "因为",
            "所以",
            "如果",
            "麻烦",
            "谢谢",
            "您好",
            "你好",
            "现在",
            "之后",
            "之前",
            "关于",
            "对于",
            "多少",
            "几个",
            "一直",
            "还有",
            "已经",
            "是",
            "的",
            "了",
            "吗",
            "呢",
            "吧",
            "啊",
            "呀",
            "哦",
            "嗯",
            "流程",
            "方式",
            "方法"
    ));

    /**
     * 领域同义词（双向展开）。
     *
     * <p>📌 **纯字面匹配处理不了同义表达**：
     * 「退钱流程是什么」和「如何申请退款？」**一个共同字都没有** →
     * 任何 n-gram 方案都是 0 分 → 用户永远问不到那条知识。
     *
     * <p>这里是**关键词检索**（不是向量检索），所以用一份**领域词表**补齐
     * 最常见的口语↔术语映射。词表小而准，优先覆盖客服高频问法。
     */
    private static final Map<String, List<String>> DOMAIN_SYNONYMS = Map.ofEntries(
            Map.entry("退钱", List.of("退款", "退货")),
            Map.entry("退货", List.of("退款", "售后")),
            Map.entry("查订单", List.of("查询订单", "订单查询")),
            Map.entry("订单号", List.of("订单编号")),
            Map.entry("几天", List.of("多久", "时效")),
            Map.entry("什么时候到", List.of("多久", "时效", "送达")),
            Map.entry("收到货", List.of("签收", "收到")),
            Map.entry("多少钱", List.of("价格", "费用")),
            Map.entry("价格", List.of("多少钱", "费用")),
            Map.entry("库存不足", List.of("缺货", "补货")),
            Map.entry("没货", List.of("缺货", "库存不足")),
            Map.entry("怎么取消", List.of("取消订单", "撤销")),
            Map.entry("开发票", List.of("发票", "开票")),
            Map.entry("邮费", List.of("运费", "shipping")),
            Map.entry("运费", List.of("邮费", "shipping")),
            Map.entry("优惠", List.of("折扣", "促销")),
            Map.entry("尺寸", List.of("规格", "尺码")),
            Map.entry("尺码", List.of("尺寸", "规格")),
            Map.entry("上门", List.of("取货", "自提")),
            Map.entry("自提", List.of("上门", "取货"))
    );

    /**
     * 查询与**标签列表**的最大相似度。
     *
     * <p>📌 标签是很强的召回信号：用户问「退钱流程是什么」，
     * 问题文本里没有共同字，但标签「退款」能把它拉回来。
     *
     * @param tags 知识条目的标签（可能为 null / 空）
     * @return 与所有标签的最大相似度，没有标签时返回 0
     */
    private double tagsSimilarity(String query, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return 0;
        }
        double best = 0;
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                best = Math.max(best, calculateSimilarity(query, tag));
            }
        }
        return best;
    }

    /**
     * 把文本里的同义词展开成多个变体。
     *
     * <p>📌 同义词是**双向**的：知识库写「退款」，用户问「退钱」也要能匹配上，
     * 所以**双方都展开**。
     */
    private List<String> expandSynonyms(String text) {
        List<String> variants = new ArrayList<>();
        variants.add(text);
        String lower = text.toLowerCase();
        for (Map.Entry<String, List<String>> entry : DOMAIN_SYNONYMS.entrySet()) {
            String key = entry.getKey().toLowerCase();
            List<String> alts = entry.getValue();
            if (lower.contains(key)) {
                for (String alt : alts) {
                    // 替换成同义词，得到一个「变体文本」
                    String replaced = text.replace(entry.getKey(), alt);
                    if (!replaced.equals(text)) {
                        variants.add(replaced);
                    }
                }
            }
            // 反向：文本里出现的是同义词，本身也带上 key
            for (String alt : alts) {
                if (lower.contains(alt.toLowerCase()) && !text.contains(entry.getKey())) {
                    String replaced = text.replace(alt, entry.getKey());
                    if (!replaced.equals(text)) {
                        variants.add(replaced);
                    }
                }
            }
        }
        return variants;
    }

    /**
     * 计算相似度（字符级 n-gram 相似度）。
     *
     * 📌 **为什么不能用「按空格切词 + Jaccard」**（2026-11-04 实测踩坑）：
     *
     * <pre>
     *   query = "订单怎么查"          text = "如何查询我的订单？"
     *   split("\\s+") → ["订单怎么查"] vs ["如何查询我的订单？"]
     *   交集 = ∅  → 相似度 = 0.000  → 永远检索不到
     * </pre>
     *
     * <b>中文没有空格</b> —— 按空白切词对中文完全失效：
     * 只要用户的问法跟种子里那条不完全一致（几乎必然），
     * 相似度就是 0 → **RAG 检索永远返回空** → 助手退化成纯 LLM 闲聊。
     *
     * 📌 实测（真实 Java 代码跑的）：
     * <pre>
     *   "如何查询我的订单？"        → 命中 1   （逐字相同）
     *   "订单怎么查"                → 命中 0   ❌
     *   "退款怎么申请"              → 命中 0   ❌
     *   "发货 多久"                 → 命中 0   ❌（就算手动加空格也不行）
     * </pre>
     *
     * <p>📌 **现在改成字符级 n-gram**：
     * <ul>
     *   <li><b>中日韩</b>：取**相邻两字**的 bigram（「订单怎么查」→「订单/单怎/怎么/么查」）</li>
     *   <li><b>拉丁文</b>：仍按空格切词（英文靠词根，bigram 会误匹配）</li>
     *   <li>其余字符（数字/符号）归入「其他 token」</li>
     * </ul>
     * 分数取 **Dice 与包含度的较大值**：
     * <ul>
     *   <li>Dice = 2|A∩B|/(|A|+|B|) —— 整体相似度</li>
     *   <li>包含度 = |A∩B|/min(|A|,|B|) —— 「短查询被长文本覆盖」的程度
     *       （用户只问「退款」两个字，应该能命中那条很长的答案）</li>
     * </ul>
     */
    private double calculateSimilarity(String query, String text) {
        if (query == null || text == null) {
            return 0;
        }
        double best = 0;
        for (String qVariant : expandSynonyms(query)) {
            for (String tVariant : expandSynonyms(text)) {
                best = Math.max(best, similarityOf(qVariant, tVariant));
            }
        }
        return best;
    }

    /** Dice 与包含度取大 */
    private double similarityOf(String query, String text) {
        Set<String> queryTokens = tokenize(query);
        Set<String> textTokens = tokenize(text);
        if (queryTokens.isEmpty() || textTokens.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(queryTokens);
        intersection.retainAll(textTokens);
        if (intersection.isEmpty()) {
            return 0;
        }
        double dice = 2.0 * intersection.size() / (queryTokens.size() + textTokens.size());
        // 📌 **覆盖度必须按「查询侧」算**（|A∩B| / |A|），不能用 min(|A|,|B|)：
        //    min 版会把「长查询只命中一个泛用词」也算成满分 ——
        //    实测「退钱流程是什么」命中标签「流程」→ min 版给 0.9，
        //    把真正该答的「如何申请退款？」挤到第二。
        //    按查询侧算：长查询只命中 1 个词 → 1/5 = 0.2 → 排不上去 ✅
        //    而「退款」这种短查询整串命中 → 1/1 = 1 → 仍然强力命中 ✅
        double coverage = (double) intersection.size() / queryTokens.size();
        return Math.max(dice, coverage * CONTAINMENT_WEIGHT);
    }

    /**
     * 把文本切成用于相似度比较的 token 集合。
     *
     * <p>中文/日文/韩文 → **相邻两字的 bigram**；拉丁字母 → **按空格/标点切词**；
     * 其余（数字、符号）→ 归为单字符 token。
     *
     * <p>📌 单字（unigram）会太宽松（「退」能匹配「退款」也能匹配「退货退款」），
     * 所以中文用 **bigram**——既能识别「退款」也能识别「怎么申请」。
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        String normalized = text.toLowerCase().trim();
        if (normalized.isEmpty()) {
            return tokens;
        }

        // 拉丁词（连续字母）单独处理
        java.util.regex.Matcher matcher = LATIN_WORD.matcher(normalized);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        // 去掉拉丁词之后剩下的部分 —— 中文、数字、标点
        String rest = LATIN_WORD.matcher(normalized).replaceAll(" ");

        // 逐字符扫描：**空白是分隔符** → 每个「连续非拉丁段」各自做 bigram
        // 📌 必须在扫描过程中**即时**产出 bigram ——
        //    如果先扫完再统一切，空白前面的段会被后面的段覆盖掉
        //    （「发货 多久」只剩「多久」）
        int segStart = -1;
        for (int i = 0; i <= rest.length(); i++) {
            boolean isSep = (i == rest.length()) || Character.isWhitespace(rest.charAt(i));
            if (!isSep) {
                if (segStart < 0) {
                    segStart = i;
                }
                continue;
            }
            if (segStart >= 0) {
                int len = i - segStart;
                if (len == 1) {
                    tokens.add(rest.substring(segStart, segStart + 1));
                } else {
                    for (int j = segStart; j + 1 < i; j++) {
                        tokens.add(rest.substring(j, j + 2));
                    }
                }
                segStart = -1;
            }
        }

        // 📌 滤掉低信息量 token（疑问句式等）—— 否则短查询会被「如何」带偏
        tokens.removeIf(STOPWORD_TOKENS::contains);

        // 兜底：完全没切出任何东西（如纯符号）→ 退化成整串
        if (tokens.isEmpty()) {
            tokens.add(normalized);
        }
        return tokens;
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
