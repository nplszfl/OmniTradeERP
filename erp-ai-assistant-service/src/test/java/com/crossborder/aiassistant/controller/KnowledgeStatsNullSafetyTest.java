package com.crossborder.aiassistant.controller;

import com.crossborder.aiassistant.config.LLMConfig;
import com.crossborder.aiassistant.dto.KnowledgeStats;
import com.crossborder.aiassistant.entity.KnowledgeBase;
import com.crossborder.aiassistant.entity.KnowledgeDocument;
import com.crossborder.aiassistant.repository.KnowledgeBaseRepository;
import com.crossborder.aiassistant.repository.KnowledgeDocumentRepository;
import com.crossborder.aiassistant.service.EmbeddingService;
import com.crossborder.aiassistant.service.RAGService;
import com.crossborder.aiassistant.service.VectorSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 统计接口的**空值安全**回归测试（2026-11-11）
 *
 * <p>📌 锁的是**规则 16**：`Collectors.groupingBy` 的**分类键为 null 会抛 NPE**
 * （JDK 的 {@code groupingBy} 内部按 key 分桶，null key 直接 NPE，不是「归到 null 桶」）。
 *
 * <p>为什么这值得单独一个测试：
 * <ul>
 *   <li>DDL 里 {@code knowledge_base.language} 与 {@code knowledge_document.category}
 *       都是**可空**列（{@code DEFAULT 'zh'} / {@code DEFAULT NULL}）→ 数据层**不保证**非 null；</li>
 *   <li>统计代码里靠 {@code .filter(x -> x.getXxx() != null)} 挡住；</li>
 *   <li>一旦有人删掉那个 filter → **统计接口直接 500**，
 *       而「统计」这种看着人畜无害的代码**很难在 review 里被看出来**（规则 44 / 104）。</li>
 * </ul>
 *
 * <p>📌 同时把**口径**钉死（避免以后有人改统计口径时无声漂移）：
 * <ul>
 *   <li>{@code totalCount} / {@code activeCount} **包含**那些 null 键的行；</li>
 *   <li>{@code categoryCount} / {@code languageCount} **不含** null 键的行
 *       （所以「按分类求和」可能小于 {@code activeCount} —— 这是**当前语义**，不是 bug）。</li>
 * </ul>
 */
class KnowledgeStatsNullSafetyTest {

    private KnowledgeBase base(long id, String category, String language, boolean active) {
        return KnowledgeBase.builder()
                .id(id).category(category).language(language)
                .question("问题" + id).answer("答案" + id)
                .tags(List.of("标签")).priority(5).active(active).build();
    }

    private KnowledgeDocument doc(long id, String category, String language, boolean active) {
        KnowledgeDocument d = new KnowledgeDocument();
        d.setId(id);
        d.setTitle("标题" + id);
        d.setContent("正文" + id);
        d.setCategory(category);
        d.setLanguage(language);
        d.setActive(active);
        return d;
    }

    @Test
    @DisplayName("知识库统计：category/language 为 null 的行不会让 groupingBy 抛 NPE")
    void knowledgeBaseStatsToleratesNullCategoryAndLanguage() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                base(1L, "订单", "zh", true),
                base(2L, null, null, true),      // ← 可空列：分类和语言都可能是 null
                base(3L, "物流", "zh", false)));   // ← 未启用行

        KnowledgeController controller = new KnowledgeController(mock(RAGService.class), repository);
        KnowledgeStats stats = controller.stats().getBody();

        assertNotNull(stats);
        assertEquals(3L, stats.getTotalCount(), "总条数应包含 null 键的行");
        assertEquals(2L, stats.getActiveCount(), "启用条数应包含 null 键的启用行");

        Map<String, Long> cat = stats.getCategoryCount();
        Map<String, Long> lang = stats.getLanguageCount();
        assertNotNull(cat);
        assertNotNull(lang);
        assertEquals(1L, cat.get("订单"));
        assertEquals(1L, lang.get("zh"));
        // 📌 null 键**不进 map**（否则前端 chart 的 label 会变成 "null"）
        assertFalse(cat.containsKey(null), "categoryCount 不应含 null 键");
        assertFalse(lang.containsKey(null), "languageCount 不应含 null 键");
        assertFalse(cat.containsValue(null) || lang.containsValue(null), "计数不应为 null");
    }

    @Test
    @DisplayName("向量文档统计：category 为 null 的行不会让 groupingBy 抛 NPE")
    void documentStatsToleratesNullCategory() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                doc(1L, "订单", "zh", true),
                doc(2L, null, "zh", true),     // ← knowledge_document.category 可空
                doc(3L, "物流", "zh", false)));

        RAGService ragService = new RAGService(repository,
                mock(EmbeddingService.class), mock(VectorSearchService.class), mock(LLMConfig.class));
        KnowledgeStats stats = ragService.stats();

        assertNotNull(stats);
        assertEquals(3L, stats.getTotalCount());
        assertEquals(2L, stats.getActiveCount());
        assertEquals(1L, stats.getCategoryCount().get("订单"));
        assertEquals(2L, stats.getLanguageCount().get("zh"));
        assertFalse(stats.getCategoryCount().containsKey(null), "categoryCount 不应含 null 键");
    }
}
