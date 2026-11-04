package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.config.LLMConfig;
import com.crossborder.aiassistant.dto.RAGAnswer;
import com.crossborder.aiassistant.dto.RetrievalResult;
import com.crossborder.aiassistant.entity.KnowledgeDocument;
import com.crossborder.aiassistant.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RAGService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RAGService 单元测试")
class RAGServiceTest {

    @Mock private KnowledgeDocumentRepository repository;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorSearchService vectorSearchService;
    /**
     * 📌 LLMConfig（2026-11-04 新增依赖）：
     * 之前 RAGService **根本没有 LLM 依赖** —— answer() 只是把上下文
     * 拼成一段提示词就返回，压根没调过 LLM。
     */
    @Mock private LLMConfig llmConfig;
    @InjectMocks private RAGService service;

    // ---------- backfillMissingEmbeddings ----------

    /**
     * 📌 **回归测试：旧维度的 embedding 必须被重算**（2026-11-04 修的真缺陷）。
     *
     * <p><b>缺陷</b>：embedding 实现从 SHA-256 的 <b>384 维</b>换成特征哈希的 <b>1024 维</b>，
     * 而回填的判据原本是 {@code embedding != null → skip}
     * → 库里旧算法算的 384 维向量<b>非 NULL</b> → <b>永远不会被重算</b>
     * → {@code VectorSearchService} 的维度检查
     * {@code docVec.size() != queryVec.size() → continue}
     * → <b>这些老文档永久失联</b>，而且只有一行 warn 日志。
     *
     * <p>→ 判据必须是「能不能和当前查询向量对上」，而不是「是不是 NULL」。
     */
    @Test
    @DisplayName("回填会重算旧维度（384维）的 embedding")
    void testBackfill_重算旧维度embedding() {
        // 旧算法算的 384 维向量（非 NULL！）
        String old384 = buildEmbeddingJson(384);
        KnowledgeDocument stale = new KnowledgeDocument();
        stale.setId(1L);
        stale.setTitle("订单查询指南");
        stale.setContent("在「我的订单」页面查看");
        stale.setEmbedding(old384);
        stale.setActive(true);

        when(repository.findByActiveTrue()).thenReturn(List.of(stale));
        when(embeddingService.getDimension()).thenReturn(1024);
        when(embeddingService.embed(anyString()))
                .thenReturn(buildVector(1024));

        service.backfillMissingEmbeddings();

        // 📌 必须被重算成 1024 维
        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(repository).save(captor.capture());
        String newEmbedding = captor.getValue().getEmbedding();
        assertNotNull(newEmbedding);
        assertEquals(1024, VectorSearchService.parseEmbedding(newEmbedding).size(),
                "❌ 旧维度（384）的 embedding 没有被重算成 1024 维 → 这篇文档永远检索不到");
    }

    /**
     * 📌 **回归测试：维度已经对的 embedding 不应被无谓重算**。
     *
     * <p>否则每次启动都要重算全部文档 → 启动变慢 + 无谓的写库。
     */
    @Test
    @DisplayName("回填跳过维度已经正确的 embedding")
    void testBackfill_跳过维度正确的embedding() {
        KnowledgeDocument ok = new KnowledgeDocument();
        ok.setId(1L);
        ok.setTitle("订单查询指南");
        ok.setContent("在「我的订单」页面查看");
        ok.setEmbedding(buildEmbeddingJson(1024));
        ok.setActive(true);

        when(repository.findByActiveTrue()).thenReturn(List.of(ok));
        when(embeddingService.getDimension()).thenReturn(1024);

        service.backfillMissingEmbeddings();

        verify(repository, never()).save(any());
    }

    /**
     * 📌 **回归测试：回填必须用和入库路径一样的同义词扩展**。
     *
     * <p>不一致 → 同一段文字，回填的能被检索到、API 新增的检索不到（或反之）
     * → 表现就是「有些知识能搜到、有些搜不到」，而且没有任何报错。
     */
    @Test
    @DisplayName("回填用 DomainSynonyms.expandForEmbedding（和入库路径一致）")
    void testBackfill_使用同义词扩展() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setTitle("产品售后政策");
        // ⚠️ 这里**不能**出现「退款」二字 ——
        //   否则断言「嵌入文本含退款」会**恒真** → 测不出扩展到底有没有发生
        doc.setContent("退换货与售后处理说明");
        doc.setEmbedding(null);
        doc.setActive(true);

        when(repository.findByActiveTrue()).thenReturn(List.of(doc));
        when(embeddingService.getDimension()).thenReturn(1024);
        when(embeddingService.embed(anyString())).thenReturn(buildVector(1024));

        service.backfillMissingEmbeddings();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(embeddingService).embed(captor.capture());
        String embedded = captor.getValue();
        // 📌 原文只有「退换货/售后」，**没有**「退款」二字
        //   → 扩展后必须多出「退款」→ 证明扩展确实发生了
        //   （注意：断言的是**扩展后的文本**，不能反过来断言它不含「退款」）
        assertTrue(embedded.contains("退款"),
                "❌ 回填时没有做同义词扩展 → 和入库路径不一致 → 检索表现不一致。实际嵌入文本 = " + embedded);
    }

    private static List<Double> buildVector(int dim) {
        List<Double> v = new java.util.ArrayList<>(dim);
        for (int i = 0; i < dim; i++) {
            v.add(0.01 * (i % 7));
        }
        return v;
    }

    private static String buildEmbeddingJson(int dim) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < dim; i++) {
            if (i > 0) sb.append(",");
            sb.append(0.01 * (i % 7));
        }
        return sb.append("]").toString();
    }

    // ---------- addDocument ----------

    @Test
    @DisplayName("addDocument 自动算 embedding 并持久化")
    void testAddDocumentAutoEmbeds() {
        KnowledgeDocument input = KnowledgeDocument.builder()
                .title("运费政策")
                .content("国际订单满 99 美元免运费")
                .category("物流")
                .language("zh")
                .build();
        when(embeddingService.embed(anyString())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(repository.save(any(KnowledgeDocument.class))).thenAnswer(inv -> {
            KnowledgeDocument d = inv.getArgument(0);
            d.setId(100L);
            return d;
        });

        KnowledgeDocument saved = service.addDocument(input);

        // 验证 embedding 被计算并塞回去
        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(repository).save(captor.capture());
        assertNotNull(captor.getValue().getEmbedding());
        assertTrue(captor.getValue().getEmbedding().contains("0.1"));

        assertEquals(100L, saved.getId());
        assertNotNull(saved.getEmbedding());
    }

    @Test
    @DisplayName("addDocument 拒绝空内容")
    void testAddDocumentRejectsBlankContent() {
        assertThrows(IllegalArgumentException.class, () ->
                service.addDocument(KnowledgeDocument.builder().content("").build()));
        assertThrows(IllegalArgumentException.class, () ->
                service.addDocument(null));
    }

    @Test
    @DisplayName("addDocument 默认 active=true、language=zh")
    void testAddDocumentDefaults() {
        KnowledgeDocument input = KnowledgeDocument.builder()
                .title("t").content("c").build();
        when(embeddingService.embed(anyString())).thenReturn(List.of(0.0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addDocument(input);

        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(repository).save(captor.capture());
        assertEquals(true, captor.getValue().getActive());
        assertEquals("zh", captor.getValue().getLanguage());
    }

    @Test
    @DisplayName("addDocumentsBatch 批量入库")
    void testAddBatch() {
        KnowledgeDocument a = KnowledgeDocument.builder().title("a").content("ca").build();
        KnowledgeDocument b = KnowledgeDocument.builder().title("b").content("cb").build();
        when(embeddingService.embed(anyString())).thenReturn(List.of(0.0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<KnowledgeDocument> result = service.addDocumentsBatch(List.of(a, b));

        assertEquals(2, result.size());
        verify(repository, times(2)).save(any());
    }

    @Test
    @DisplayName("addDocumentsBatch 空列表返回空")
    void testAddBatchEmpty() {
        assertTrue(service.addDocumentsBatch(null).isEmpty());
        assertTrue(service.addDocumentsBatch(List.of()).isEmpty());
        verify(repository, never()).save(any());
    }

    // ---------- retrieve ----------

    @Test
    @DisplayName("retrieve 委托给 VectorSearchService 并转换结果")
    void testRetrieveDelegates() {
        KnowledgeDocument d = KnowledgeDocument.builder()
                .id(1L).title("t").content("c").category("订单").language("zh").build();
        when(vectorSearchService.search(eq("订单"), anyInt(), anyDouble()))
                .thenReturn(List.of(new VectorSearchService.SearchResult(d, 0.95)));

        List<RetrievalResult> out = service.retrieve("订单", 3);

        assertEquals(1, out.size());
        assertEquals(1L, out.get(0).getDocumentId());
        assertEquals("订单", out.get(0).getCategory());
        assertEquals(0.95, out.get(0).getScore(), 1e-6);
    }

    // ---------- answer ----------

    @Test
    @DisplayName("answer 未配置 LLM 时如实降级（mock=true），**不再把提示词当答案返回**")
    void testAnswerWithContext() {
        KnowledgeDocument d = KnowledgeDocument.builder()
                .id(1L).title("退款流程").content("7 天内可申请退款")
                .category("退款").language("zh").build();
        when(vectorSearchService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(new VectorSearchService.SearchResult(d, 0.88)));
        // 📌 未配置 LLM → 必须走降级分支
        when(llmConfig.isConfigured()).thenReturn(false);

        RAGAnswer ans = service.answer("怎么退款", 3);

        assertTrue(ans.isHasContext());
        assertEquals(1, ans.getRetrievedDocs().size());
        assertNotNull(ans.getAnswer());
        assertTrue(ans.getAnswer().contains("退款"));
        // 📌 **关键断言**：降级必须**可见**
        assertTrue(ans.getMock(), "❌ 未配置 LLM 时 mock 必须为 true（调用方要能分辨真假）");
        assertNotNull(ans.getMockReason());
        // 📌 **更关键**：不能把提示词当答案返回
        assertFalse(ans.getAnswer().contains("根据以下知识库内容回答用户问题"),
                "❌ 答案不能是「给 LLM 的指令」—— 旧实现就是这样，用户看到的是提示词不是答案");
        assertFalse(ans.getAnswer().contains("用户问题："),
                "❌ 答案不能把用户问题原样拼回去（旧实现的问题）");
    }

    /**
     * 📌 **回归测试：未配置 LLM 时不能把提示词当答案**（2026-11-04 修的真缺陷）。
     *
     * <p>旧实现 {@code composeAnswerWithContext} 直接被当成返回值 →
     * 用户看到的是「根据以下知识库内容回答用户问题：……」这种<b>给 LLM 的指令</b>，
     * 而 {@code hasContext=true} 又让人以为 AI 真的基于知识库回答了
     * → <b>一个假的成功信号</b>。
     */
    @Test
    @DisplayName("answer 绝不返回「提示词」当答案")
    void testAnswerNeverReturnsPrompt() {
        KnowledgeDocument d = KnowledgeDocument.builder()
                .id(1L).title("退款流程").content("7 天内可申请退款")
                .category("退款").language("zh").build();
        when(vectorSearchService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(new VectorSearchService.SearchResult(d, 0.88)));
        when(llmConfig.isConfigured()).thenReturn(false);

        RAGAnswer ans = service.answer("怎么退款", 3);

        assertFalse(ans.getAnswer().contains("根据以下知识库内容回答用户问题"),
                "❌ 不能把 LLM 指令当答案返回");
        assertFalse(ans.getAnswer().contains("用户问题：怎么退款"),
                "❌ 不能把用户问题原样拼回去");
        // 📌 降级时至少要把检索到的**内容**列出来（可读的事实）
        assertTrue(ans.getAnswer().contains("7 天内可申请退款"),
                "❌ 降级答案应该至少列出检索到的内容");
    }

    @Test
    @DisplayName("answer 无上下文时返回兜底 + hasContext=false")
    void testAnswerWithoutContext() {
        when(vectorSearchService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        RAGAnswer ans = service.answer("玄学问题", 3);

        assertFalse(ans.isHasContext());
        assertTrue(ans.getRetrievedDocs().isEmpty());
        assertNotNull(ans.getAnswer());
        assertTrue(ans.getAnswer().contains("未找到") || ans.getAnswer().length() > 0);
    }

    // ---------- softDelete ----------

    @Test
    @DisplayName("softDelete 存在时置 active=false 并保存")
    void testSoftDeleteExisting() {
        KnowledgeDocument d = KnowledgeDocument.builder()
                .id(42L).title("x").content("y").active(true).build();
        when(repository.findById(42L)).thenReturn(Optional.of(d));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean ok = service.softDelete(42L);

        assertTrue(ok);
        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(repository).save(captor.capture());
        assertEquals(false, captor.getValue().getActive());
    }

    @Test
    @DisplayName("softDelete 不存在时返回 false")
    void testSoftDeleteNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertFalse(service.softDelete(99L));
        verify(repository, never()).save(any());
    }

    // ---------- stats ----------

    @Test
    @DisplayName("stats 正确按 category/language 分组")
    void testStats() {
        when(repository.findAll()).thenReturn(List.of(
                docOf(1L, "订单", "zh", true),
                docOf(2L, "订单", "zh", true),
                docOf(3L, "物流", "en", true),
                docOf(4L, "订单", "zh", false)   // 不计入 active
        ));

        var stats = service.stats();

        assertEquals(4L, stats.getTotalCount());
        assertEquals(3L, stats.getActiveCount());
        assertEquals(2L, stats.getCategoryCount().get("订单"));
        assertEquals(1L, stats.getCategoryCount().get("物流"));
        assertEquals(2L, stats.getLanguageCount().get("zh"));
        assertEquals(1L, stats.getLanguageCount().get("en"));
    }

    // ---------- 工具 ----------

    private static KnowledgeDocument docOf(Long id, String cat, String lang, boolean active) {
        return KnowledgeDocument.builder()
                .id(id).title("t" + id).content("c" + id)
                .category(cat).language(lang).active(active).build();
    }
}
