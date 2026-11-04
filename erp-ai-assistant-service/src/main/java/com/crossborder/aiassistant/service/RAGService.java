package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.config.LLMConfig;
import com.crossborder.aiassistant.dto.KnowledgeStats;
import com.crossborder.aiassistant.dto.RAGAnswer;
import com.crossborder.aiassistant.dto.RetrievalResult;
import com.crossborder.aiassistant.entity.KnowledgeDocument;
import com.crossborder.aiassistant.repository.KnowledgeDocumentRepository;
import com.crossborder.erp.llm.LLMEndpointResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 服务 - 主入口
 * <p>
 * 负责：
 * <ul>
 *   <li>文档入库（自动算 embedding）</li>
 *   <li>检索 + 回答生成</li>
 *   <li>软删除 + 统计</li>
 * </ul>
 *
 * @author OmniTrade AI Team
 * @since 1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    private final KnowledgeDocumentRepository repository;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final LLMConfig llmConfig;

    /** 📌 RAG 调 LLM 用（与 {@code AIAssistantServiceImpl} 同款） */
    private final RestTemplate restTemplate = new RestTemplate();

    /** 默认检索 topK */
    private static final int DEFAULT_TOPK = 3;
    /**
     * 📌 默认相似度阈值 = **0.05**（2026-11-04 从 0.5 改下来）。
     *
     * <p><b>为什么必须降：</b>旧实现是 SHA-256 随机向量，
     * 任意两篇文档的余弦相似度都在 0.62~0.69 → 阈值 0.5 形同虚设
     * → 每篇文档都“命中” → 检索 = 全量返回 + 随机排序。
     *
     * <p><b>换成特征哈希后真实分数区间是：</b>
     * <pre>
     *   相关文档  ≈ 0.15 ~ 0.30
     *   不相关    ≈ 0.00 ~ 0.05（共享片段时略高）
     * </pre>
     * → 阈值 0.5 会把**所有**文档都过滤掉 → 检索永远返回空。
     * → 0.05 能滤掉“完全无共同片段”的，同时保留真正相关的。
     *
     * <p>📌 这是一个**经验值**，跟 embedding 实现强绑定。
     * 换回真实语义 embedding 后，这个值要重新标定
     * （真实 embedding 的余弦相似度分布完全不同）。
     */
    private static final double DEFAULT_THRESHOLD = 0.05;

    /**
     * 入库单个文档，自动计算 embedding
     */
    @Transactional
    public KnowledgeDocument addDocument(KnowledgeDocument doc) {
        if (doc == null || doc.getContent() == null || doc.getContent().isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        if (doc.getActive() == null) doc.setActive(true);
        if (doc.getLanguage() == null) doc.setLanguage("zh");

        // 算 embedding — 标题+内容 拼接喂给模型
        String textToEmbed = (doc.getTitle() == null ? "" : doc.getTitle()) + "\n" + doc.getContent();
        // 📌 **文档侧也做同义词扩展**（2026-11-04 修）
        //   只扩展查询侧、不扩展文档侧 → 两边的特征空间不对齐
        //   → 扩展后的查询反而可能匹配不上任何文档
        List<Double> vec = embeddingService.embed(DomainSynonyms.expandForEmbedding(textToEmbed));
        doc.setEmbedding(serializeEmbedding(vec));

        KnowledgeDocument saved = repository.save(doc);
        log.info("文档入库 id={} title='{}' category={}", saved.getId(), saved.getTitle(), saved.getCategory());
        return saved;
    }

    /**
     * 批量入库
     */
    @Transactional
    public List<KnowledgeDocument> addDocumentsBatch(List<KnowledgeDocument> docs) {
        if (docs == null || docs.isEmpty()) return new ArrayList<>();
        List<KnowledgeDocument> saved = new ArrayList<>(docs.size());
        for (KnowledgeDocument d : docs) {
            saved.add(addDocument(d));
        }
        log.info("批量入库完成 数量={}", saved.size());
        return saved;
    }

    /**
     * 检索（不生成回答）
     */
    public List<RetrievalResult> retrieve(String query, int topK) {
        List<VectorSearchService.SearchResult> raw =
                vectorSearchService.search(query, topK > 0 ? topK : DEFAULT_TOPK, DEFAULT_THRESHOLD);
        List<RetrievalResult> out = new ArrayList<>(raw.size());
        for (VectorSearchService.SearchResult r : raw) {
            KnowledgeDocument d = r.getDocument();
            out.add(RetrievalResult.builder()
                    .documentId(d.getId())
                    .title(d.getTitle())
                    .content(d.getContent())
                    .category(d.getCategory())
                    .language(d.getLanguage())
                    .score(r.getScore())
                    .build());
        }
        return out;
    }

    /**
     * RAG 主入口：检索 + <b>真实调 LLM 生成回答</b>。
     *
     * <p>📌 <b>2026-11-04 修的真缺陷</b>：
     * 旧实现 {@code answer()} 根本没有 LLM 依赖（只有 repository / embedding / vectorSearch），
     * {@code composeAnswerWithContext} 只是把上下文<b>拼成一段提示词就返回了</b>：
     * <pre>{@code
     * answer = "根据以下知识库内容回答用户问题：\n\n【1】xxx\nyyy\n\n用户问题：zzz"
     * }</pre>
     * → 用户看到的是一段<b>给 LLM 的指令</b>，不是答案；
     * → 而 {@code hasContext=true} 又让人以为「AI 真的基于知识库回答了」
     * → <b>一个假的成功信号</b>（规则 44：文档说做了 A，代码做的是 B）。
     *
     * <p>→ 现在真的去调 LLM；调不通时<b>如实降级</b>：
     * {@code mock=true} + {@code mockReason}，调用方看得见（规则 19）。
     */
    public RAGAnswer answer(String query, int topK) {
        long start = System.currentTimeMillis();
        log.info("RAG answer 入口 query='{}' topK={}", query, topK);

        List<RetrievalResult> retrieved = retrieve(query, topK);
        boolean hasContext = !retrieved.isEmpty();

        String answer;
        boolean mock;
        String mockReason = null;

        if (!hasContext) {
            // 检索不到 → 明确告知「没找到」，不假装有答案
            answer = "未找到与问题相关的知识库内容。请尝试换个问法，或联系客服。";
            mock = false;
        } else if (!llmConfig.isConfigured()) {
            // ⚠️ 没有 LLM → **不能返回提示词冒充答案**（规则 19）
            answer = buildFallbackAnswer(retrieved);
            mock = true;
            mockReason = "未配置 LLM API Key 或 llm.enabled=false";
            log.warn("RAG 降级为检索结果直出：{}", mockReason);
        } else {
            try {
                answer = callLLM(query, retrieved);
                mock = false;
            } catch (Exception e) {
                log.error("RAG 调用 LLM 失败，降级为检索结果直出", e);
                answer = buildFallbackAnswer(retrieved);
                mock = true;
                mockReason = "真实 LLM 调用失败：" + e.getMessage();
            }
        }

        return RAGAnswer.builder()
                .query(query)
                .answer(answer)
                .retrievedDocs(retrieved)
                .hasContext(hasContext)
                .mock(mock)
                .mockReason(mockReason)
                .responseTime(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * 真实调 LLM 生成回答。
     *
     * <p>📌 端点拼接的唯一事实来源 = {@link LLMEndpointResolver}（不要自己拼 URL，
     * 否则会拼出双 /v1 → 必然 404 → 静默降级）。
     */
    @SuppressWarnings("unchecked")
    private String callLLM(String query, List<RetrievalResult> retrieved) {
        String apiUrl = LLMEndpointResolver.chatCompletionsUrl(
                llmConfig.getBaseUrl(),
                llmConfig.getProvider(),
                llmConfig.getDeployment(),
                llmConfig.getApiVersion());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());
        requestBody.put("temperature", llmConfig.getTemperature());
        requestBody.put("max_tokens", llmConfig.getMaxTokens());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是OmniTrade跨境电商ERP系统的AI客服助手火球鼠。"
                        + "请**只根据下面提供的知识库内容**回答用户问题。"
                        + "如果知识库内容不足以回答，就直接说知识库里没有相关信息，不要编造。"));
        messages.add(Map.of("role", "user", "content", composeAnswerWithContext(query, retrieved)));
        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 📌 Azure 用 `api-key` 头，其他家（OpenAI 兼容）用 `Authorization: Bearer`
        if (LLMEndpointResolver.isAzure(llmConfig.getProvider())) {
            headers.set("api-key", llmConfig.getApiKey());
        } else {
            headers.setBearerAuth(llmConfig.getApiKey());
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, new HttpEntity<>(requestBody, headers), Map.class);

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new RuntimeException("LLM API 响应体为空");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM API 响应格式错误（无 choices）");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || message.get("content") == null) {
            throw new RuntimeException("LLM API 响应格式错误（无 content）");
        }
        return String.valueOf(message.get("content"));
    }

    /**
     * 📌 **降级时的兜底**：把检索到的内容直接列出来（而不是返回提示词）。
     *
     * <p>这样至少是**可读的事实**，而不是一段「请根据以下内容回答」的指令。
     * 调用方能知道：这是检索结果，不是 AI 生成的回答（配合 {@code mock=true}）。
     */
    private String buildFallbackAnswer(List<RetrievalResult> retrieved) {
        StringBuilder sb = new StringBuilder();
        sb.append("（未启用 AI 生成，以下是知识库中检索到的相关内容）\n\n");
        for (int i = 0; i < retrieved.size(); i++) {
            RetrievalResult r = retrieved.get(i);
            sb.append("【").append(i + 1).append("】")
              .append(r.getTitle() == null ? "" : r.getTitle()).append("\n")
              .append(r.getContent() == null ? "" : r.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 拼接上下文，给 LLM 用 */
    private String composeAnswerWithContext(String query, List<RetrievalResult> retrieved) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据以下知识库内容回答用户问题：\n\n");
        for (int i = 0; i < retrieved.size(); i++) {
            RetrievalResult r = retrieved.get(i);
            sb.append("【").append(i + 1).append("】")
              .append(r.getTitle()).append("\n")
              .append(r.getContent()).append("\n\n");
        }
        sb.append("用户问题：").append(query);
        return sb.toString();
    }

    /**
     * 软删除
     */
    @Transactional
    public boolean softDelete(Long id) {
        return repository.findById(id).map(d -> {
            d.setActive(false);
            repository.save(d);
            log.info("软删除文档 id={}", id);
            return true;
        }).orElse(false);
    }

    /**
     * 统计
     */
    public KnowledgeStats stats() {
        List<KnowledgeDocument> all = repository.findAll();
        long total = all.size();
        long active = all.stream().filter(d -> Boolean.TRUE.equals(d.getActive())).count();
        Map<String, Long> catCount = all.stream()
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .filter(d -> d.getCategory() != null)
                .collect(Collectors.groupingBy(KnowledgeDocument::getCategory, Collectors.counting()));
        Map<String, Long> langCount = all.stream()
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .filter(d -> d.getLanguage() != null)
                .collect(Collectors.groupingBy(KnowledgeDocument::getLanguage, Collectors.counting()));

        return KnowledgeStats.builder()
                .totalCount(total)
                .activeCount(active)
                .categoryCount(catCount)
                .languageCount(langCount)
                .build();
    }

    /**
     * 📌 **启动时回填缺失/过期 embedding**（2026-11-04 修的真缺陷）。
     *
     * <p><b>缺陷 1：embedding 全是 NULL</b>
     * <br>种子 SQL 插入的 {@code knowledge_document} 5 条文档<b>没有 embedding 列</b>
     * → 全部为 {@code NULL}
     * → {@code VectorSearchService} 遇到 {@code embedding == null} 直接 <b>skip</b>
     * → <b>检索永远返回空</b>，而接口返回 200 + 空数组
     * → 前端显示「没有数据」而不是报错（规则 12：静默失败）。
     *
     * <p><b>缺陷 2：维度变了，旧数据永远检索不到</b>（更隐蔽！）
     * <br>embedding 实现从 SHA-256 的 <b>384 维</b>换成特征哈希的 <b>1024 维</b>，
     * 而 {@code VectorSearchService} 的维度检查是：
     * <pre>{@code if (docVec == null || docVec.size() != queryVec.size()) continue;}</pre>
     * → 库里那些<b>旧算法算的 384 维</b>向量（非 NULL！）会被<b>静默跳过</b>
     * → 而且 {@code embedding != null} → 原来的回填逻辑<b>也不会碰它们</b>
     * → <b>老文档永久失联</b>，而且日志只有一行 warn，看不出是「哪批」。
     *
     * <p><b>修法</b>：回填的判据不能是「是不是 NULL」，而要是
     * <b>「能不能和当前查询向量对上」</b> →
     * <ul>
     *   <li>维度不对 → <b>重算</b>（旧算法的数据必须重建，不是补 NULL）</li>
     *   <li>维度对 → 保留（已经是当前算法算的）</li>
     *   <li>内容为空 → 跳过，不硬写</li>
     *   <li>只在<b>启动时</b>做，不是每次检索（否则每次都要扫全表）</li>
     *   <li>失败不阻断启动 —— 知识库只是功能，不是硬依赖</li>
     * </ul>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingEmbeddings() {
        try {
            List<KnowledgeDocument> docs = repository.findByActiveTrue();
            int patched = 0;
            int staleDim = 0;
            int expectedDim = embeddingService.getDimension();
            for (KnowledgeDocument doc : docs) {
                List<Double> existing = VectorSearchService.parseEmbedding(doc.getEmbedding());
                // 📌 判据 = 「能不能和当前查询向量对上」，而不是「是不是 NULL」
                if (existing != null && existing.size() == expectedDim) {
                    continue;   // 已经是当前算法算的 → 保留
                }
                boolean wasStale = existing != null && !existing.isEmpty();
                if (wasStale) {
                    staleDim++;
                }
                String textToEmbed = (doc.getTitle() == null ? "" : doc.getTitle())
                        + "\n" + (doc.getContent() == null ? "" : doc.getContent());
                if (textToEmbed.isBlank()) {
                    continue;
                }
                // 📌 **必须和入库路径用同一个扩展方式**（对称扩展）
                //   否则回填出来的向量和 API 新增的向量算法不一致
                //   → 同一段文字，回填的能被检索到、API 新增的检索不到（或反之）
                List<Double> vec = embeddingService.embed(
                        DomainSynonyms.expandForEmbedding(textToEmbed));
                if (vec == null || vec.isEmpty()) {
                    continue;
                }
                doc.setEmbedding(serializeEmbedding(vec));
                repository.save(doc);
                patched++;
            }
            if (staleDim > 0) {
                log.info("⚠️ 有 {} 篇文档的 embedding 是旧算法/旧维度的 → 已重算", staleDim);
            }
            if (patched > 0) {
                log.info("✅ 回填 embedding：{} 篇文档（原来检索不到的就是它们）", patched);
            } else {
                log.info("知识库 embedding 完整，无需回填（{} 篇文档）", docs.size());
            }
        } catch (Exception e) {
            // 📌 回填失败**不能阻断启动** —— 知识库是功能，不是硬依赖
            log.warn("回填 embedding 失败（不影响启动，检索可能为空）: {}", e.getMessage());
        }
    }

    /** 把 List<Double> 序列化成 JSON 数组字符串（不依赖 Jackson，手写避免循环依赖） */
    static String serializeEmbedding(List<Double> vec) {
        if (vec == null || vec.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vec.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
