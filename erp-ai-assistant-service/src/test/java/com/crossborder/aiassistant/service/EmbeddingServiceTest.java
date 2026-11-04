package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.service.impl.EmbeddingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingService 单元测试
 */
@DisplayName("EmbeddingService 单元测试")
class EmbeddingServiceTest {

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingServiceImpl();
    }

    @Test
    @DisplayName("相同文本必须产生完全相同向量（确定性）")
    void testSameTextSameVector() {
        List<Double> a = service.embed("hello world");
        List<Double> b = service.embed("hello world");
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i), b.get(i), 1e-12, "位置 " + i + " 不一致");
        }
    }

    @Test
    @DisplayName("不同文本必须产生不同向量")
    void testDifferentTextDifferentVector() {
        List<Double> a = service.embed("hello");
        List<Double> b = service.embed("world");
        boolean anyDifferent = false;
        for (int i = 0; i < a.size(); i++) {
            if (Math.abs(a.get(i) - b.get(i)) > 1e-9) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "不同文本必须至少有一个维度不同");
    }

    @Test
    @DisplayName("向量必须 L2 归一化（模=1）")
    void testNormalizedVector() {
        List<Double> v = service.embed("归一化测试");
        double norm = 0.0;
        for (double x : v) norm += x * x;
        norm = Math.sqrt(norm);
        assertEquals(1.0, norm, 1e-6, "向量 L2 范数应为 1.0");
    }

    @Test
    @DisplayName("空字符串不能崩溃")
    void testEmptyString() {
        List<Double> v = service.embed("");
        assertNotNull(v);
        assertEquals(service.getDimension(), v.size());
    }

    @Test
    @DisplayName("null 输入不能崩溃")
    void testNullInput() {
        List<Double> v = service.embed(null);
        assertNotNull(v);
        assertEquals(service.getDimension(), v.size());
    }

    @Test
    @DisplayName("批量调用返回正确数量")
    void testBatchSize() {
        List<List<Double>> result = service.embedBatch(List.of("a", "b", "c"));
        assertEquals(3, result.size());
        for (List<Double> v : result) {
            assertEquals(service.getDimension(), v.size());
        }
    }

    @Test
    @DisplayName("空批量返回空列表")
    void testEmptyBatch() {
        assertTrue(service.embedBatch(null).isEmpty());
        assertTrue(service.embedBatch(List.of()).isEmpty());
    }

    @Test
    @DisplayName("向量维度为 1024（特征哈希需要更高维度减少碰撞）")
    void testDimension() {
        assertEquals(1024, service.getDimension());
    }

    /**
     * 📌 **回归测试：相似度必须能区分「相关」和「不相关」**（2026-11-04 修的真缺陷）。
     *
     * <p>旧实现用 SHA-256 生成<b>纯随机</b>向量 →
     * 任意两篇文档的余弦相似度都在 0.62~0.69，<b>跟内容毫无关系</b>：
     * <pre>
     *   查询「如何申请退款？」
     *     +0.6616  退款政策：7天内无理由退货
     *     +0.6421  如何申请退款？…
     *     +0.6367  美国海外仓仓储费怎么算？   ← 完全不相关
     * </pre>
     * → 阈值之下<b>每篇都“命中”</b> → 检索 = 全量返回 + 随机排序。
     *
     * <p>→ 换成特征哈希后，相关文档必须<b>明显高于</b>不相关文档。
     */
    @Test
    @DisplayName("相似度必须能区分相关与不相关（旧 SHA-256 实现做不到）")
    void testSimilarityDistinguishesRelevantFromIrrelevant() {
        double relevant = cosine(
                service.embed("怎么查我的订单"),
                service.embed("订单查询指南 您可以在「我的订单」页面查看所有订单信息"));
        double irrelevant = cosine(
                service.embed("怎么查我的订单"),
                service.embed("美国海外仓仓储费怎么算 按体积计费 旺季上浮"));

        assertTrue(relevant > irrelevant,
                "❌ 相关文档相似度必须高于不相关文档：related=" + relevant
                        + " irrelevant=" + irrelevant);
        assertTrue(relevant > 0.1,
                "❌ 相关文档相似度必须显著大于 0，实际 = " + relevant);
    }

    /**
     * 📌 **回归测试：不相关文档的相似度必须接近 0**。
     *
     * <p>旧实现里任何两个文档都是 0.62+（纯随机向量的必然结果）
     * → 阈值根本不起作用。
     */
    @Test
    @DisplayName("完全不相关的两段文本相似度必须很低")
    void testUnrelatedSimilarityIsLow() {
        double s = cosine(
                service.embed("价格变动的规则是什么"),
                service.embed("库存安全库存和补货建议"));

        assertTrue(s < 0.1,
                "❌ 不相关文本的相似度应该很低，实际 = " + s
                        + "（旧 SHA-256 实现会给 0.6+，说明它根本没在做语义比较）");
    }

    /**
     * 📌 **回归测试：停用词不能让「所有文档」都看起来相关**。
     *
     * <p>中文 bigram 里「的是」「我的」这类组合几乎每篇都有
     * → 不过滤就会把所有文档的相似度一起抬起来 → 区分度消失。
     */
    @Test
    @DisplayName("停用词不能让不相关文档也变相似")
    void testStopwordsDoNotInflateSimilarity() {
        double s = cosine(
                service.embed("我的订单是在哪里查看的"),
                service.embed("价格会根据市场情况动态调整"));

        assertTrue(s < 0.2,
                "❌ 共享停用词「我的/是在」不该把不相关文档抬到 " + s);
    }

    /** 余弦相似度（向量已 L2 归一化 → 点积即可） */
    private static double cosine(List<Double> a, List<Double> b) {
        assertEquals(a.size(), b.size(), "向量维度必须一致");
        double dot = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
        }
        return dot;
    }
}
