package com.crossborder.aiassistant.service;

import com.crossborder.aiassistant.entity.KnowledgeDocument;
import com.crossborder.aiassistant.repository.KnowledgeDocumentRepository;
import com.crossborder.aiassistant.service.impl.EmbeddingServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量检索服务
 * <p>
 * 简化版实现：全量扫描 + 余弦相似度排序。
 * 适合 v1.8.0 阶段的 < 1w 条文档规模，
 * 后续接入 Milvus / Qdrant 时换底层，接口保持不变。
 *
 * @author OmniTrade AI Team
 * @since 1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final KnowledgeDocumentRepository repository;
    private final EmbeddingService embeddingService;

    /** 检索结果 */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private KnowledgeDocument document;
        private double score;
    }

    /**
     * 在所有 active=true 文档中检索 topK
     *
     * @param query     查询文本
     * @param topK      返回数量
     * @param threshold 相似度阈值（0~1），低于此分数的过滤掉
     */
    public List<SearchResult> search(String query, int topK, double threshold) {
        if (query == null || query.isBlank()) {
            log.warn("query 为空，跳过检索");
            return Collections.emptyList();
        }
        if (topK <= 0) topK = 5;

        log.info("向量检索开始 query='{}' topK={} threshold={}", query, topK, threshold);
        long start = System.currentTimeMillis();

        // 📌 **查询向量要把同义词扩展一起算进去**（2026-11-04 修）
        //   「多久能收到货」字面上匹配不上「物流时效说明」
        //   → 加上同义词（多久/时效/送达/物流）后，特征会重合 → 相似度升高
        //   → 而「同义词表」本身是**共享**的（DomainSynonyms），
        //     不再是「关键词路径能用、向量路径不能用」的两套知识
        List<Double> queryVec = embeddingService.embed(
                DomainSynonyms.expandForEmbedding(query));
        List<KnowledgeDocument> docs = repository.findByActiveTrue();
        if (docs.isEmpty()) {
            log.info("知识库为空，无可检索文档");
            return Collections.emptyList();
        }

        List<SearchResult> results = new ArrayList<>(docs.size());
        for (KnowledgeDocument doc : docs) {
            if (doc.getEmbedding() == null || doc.getEmbedding().isBlank()) {
                // 跳过没有 embedding 的脏数据
                continue;
            }
            List<Double> docVec = parseEmbedding(doc.getEmbedding());
            if (docVec == null || docVec.size() != queryVec.size()) {
                log.warn("文档 id={} embedding 维度不匹配，跳过", doc.getId());
                continue;
            }
            double score = cosineSimilarity(queryVec, docVec);
            if (score >= threshold) {
                results.add(new SearchResult(doc, score));
            }
        }

        // 按分数降序，取 topK
        List<SearchResult> top = results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("向量检索完成 命中={} 耗时={}ms", top.size(), System.currentTimeMillis() - start);
        return top;
    }

    /** 余弦相似度（向量已 L2 归一化时，cosine = dot product） */
    static double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size()) return 0.0;
        double dot = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
        }
        return dot;
    }

    /** 把存储的 JSON 字符串解回 List<Double> */
    static List<Double> parseEmbedding(String json) {
        if (json == null) return null;
        String s = json.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) return null;
        s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty()) return new ArrayList<>();
        try {
            String[] parts = s.split(",");
            List<Double> out = new ArrayList<>(parts.length);
            for (String p : parts) {
                out.add(Double.parseDouble(p.trim()));
            }
            return out;
        } catch (NumberFormatException e) {
            log.warn("embedding 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
