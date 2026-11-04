package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.service.impl.EmbeddingServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📌 **端到端验证**：真实的 {@code EmbeddingServiceImpl} + 真实文档 → 检索结果是否真的相关？
 *
 * <p>这个测试用**真实的** embedding 实现（不 mock），走
 * {@link VectorSearchService} 真实使用的余弦相似度计算，
 * 验证「相关文档排第一、不相关的排在后面」。
 *
 * <p>⚠️ 旧实现（SHA-256 随机向量）在这个测试下**必挂**：
 * 因为随机向量的相似度全是 0.62~0.69，排序等于随机，
 * 每次跑「第一名是谁」都可能不一样。
 *
 * <p>📌 这个测试的价值在于：**它用的是真实的相关性**，
 * 而不是像别的测试那样 mock 成单位向量
 * （mock 单位向量的测试**测不出**「排序对不对」）。
 */
@DisplayName("RAG 检索端到端（真实 embedding 实现）")
class RagIntegrationTest {

    private final EmbeddingServiceImpl embedding = new EmbeddingServiceImpl();

    private static final List<String[]> DOCS = List.of(
            new String[]{"订单查询指南", "您可以在「我的订单」页面查看所有订单信息，包括待付款、待发货、已发货等状态。"},
            new String[]{"物流时效说明", "一般情况下国内订单 1-3 天送达，国际订单 7-15 天。具体时效取决于物流方式与承运商。"},
            new String[]{"产品售后政策", "如果产品有质量问题，请在收到货 7 天内申请售后，我们会安排退换货。"},
            new String[]{"价格变动规则", "产品价格会根据市场情况动态调整，系统会自动优化定价策略。"},
            new String[]{"库存与补货", "您可以在库存管理页面查看所有产品的库存状态，包括当前库存、安全库存、预计到货时间。"}
    );

    @Test
    @DisplayName("每个查询的第一名都应该是真正相关的文档")
    void testTopResultIsRelevant() {
        // 查询 → 期望排第一的文档标题
        Object[][] cases = {
                {"怎么查我的订单", "订单查询指南"},
                {"价格会怎么变", "价格变动规则"},
                {"库存还有多少", "库存与补货"},
                {"多久能收到货", "物流时效说明"},
                {"我要退货", "产品售后政策"},
                {"多少钱", "价格变动规则"},
                {"退钱流程", "产品售后政策"},
        };

        for (Object[] c : cases) {
            String query = (String) c[0];
            String expected = (String) c[1];

            String best = topTitle(query);
            assertEquals(expected, best,
                    "❌ 查询「" + query + "」的第一名应该是「" + expected + "」，实际是「" + best + "」");
        }
    }

    @Test
    @DisplayName("相关文档的相似度必须明显高于不相关文档")
    void testRelevanceGapIsSignificant() {
        List<Double> qv = embedQuery("怎么查我的订单");
        double relevant = cosine(qv, embedDoc(DOCS.get(0)));
        double irrelevant = cosine(qv, embedDoc(DOCS.get(3)));

        assertTrue(relevant - irrelevant > 0.1,
                "❌ 相关度差距太小：relevant=" + relevant + " irrelevant=" + irrelevant
                        + "（旧 SHA-256 实现里两者都是 0.6+，说明它没在做相关性排序）");
    }

    /**
     * 📌 **同义词必须真的起作用**：口语查询要能召回对应文档。
     *
     * <p>「退钱流程」和「产品售后政策」一个字都不重合
     * → 纯字面匹配必然失败 → 必须靠同义词表拉回来。
     */
    @Test
    @DisplayName("同义词扩展必须把口语查询拉回正确文档")
    void testSynonymExpansionRecallsCorrectDoc() {
        String top = topTitle("退钱流程");
        assertEquals("产品售后政策", top,
                "❌ 「退钱流程」应该召回「产品售后政策」（靠同义词：退钱→退款/退货/售后）");

        String top2 = topTitle("邮费要多少");
        assertEquals("价格变动规则", top2,
                "❌ 「邮费要多少」应该召回「价格变动规则」（靠同义词：邮费→运费，多少钱→价格）");
    }

    /** 📌 复现真实链路：查询侧扩展 → embedding */
    private List<Double> embedQuery(String query) {
        return embedding.embed(DomainSynonyms.expandForEmbedding(query));
    }

    /** 📌 复现真实链路：文档侧扩展 → embedding（入库时就是这么算的） */
    private List<Double> embedDoc(String[] doc) {
        return embedding.embed(
                DomainSynonyms.expandForEmbedding(doc[0] + "\n" + doc[1]));
    }

    /** 相似度第一名 */
    private String topTitle(String query) {
        List<Double> qv = embedQuery(query);
        String best = null;
        double bestScore = -1;
        for (String[] doc : DOCS) {
            double s = cosine(qv, embedDoc(doc));
            if (s > bestScore) {
                bestScore = s;
                best = doc[0];
            }
        }
        return best;
    }

    private static double cosine(List<Double> a, List<Double> b) {
        double dot = 0.0;
        for (int i = 0; i < a.size(); i++) dot += a.get(i) * b.get(i);
        return dot;
    }
}
