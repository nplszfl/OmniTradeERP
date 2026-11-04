package com.crossborder.aiassistant.controller;

import com.crossborder.aiassistant.dto.KnowledgeStats;
import com.crossborder.aiassistant.dto.RAGAnswer;
import com.crossborder.aiassistant.dto.RetrievalResult;
import com.crossborder.aiassistant.entity.KnowledgeBase;
import com.crossborder.aiassistant.entity.KnowledgeDocument;
import com.crossborder.aiassistant.repository.KnowledgeBaseRepository;
import com.crossborder.aiassistant.service.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库 REST API
 *
 * @author OmniTrade AI Team
 * @since 1.8.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final RAGService ragService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /** 入库单个 */
    @PostMapping("/documents")
    public ResponseEntity<KnowledgeDocument> addDocument(@RequestBody KnowledgeDocument doc) {
        log.info("POST /api/ai/knowledge/documents title='{}'", doc.getTitle());
        return ResponseEntity.ok(ragService.addDocument(doc));
    }

    /** 批量入库 */
    @PostMapping("/documents/batch")
    public ResponseEntity<List<KnowledgeDocument>> addBatch(@RequestBody List<KnowledgeDocument> docs) {
        log.info("POST /api/ai/knowledge/documents/batch size={}", docs.size());
        return ResponseEntity.ok(ragService.addDocumentsBatch(docs));
    }

    /** 检索（不含生成） */
    @GetMapping("/search")
    public ResponseEntity<List<RetrievalResult>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        return ResponseEntity.ok(ragService.retrieve(query, topK));
    }

    /** RAG 问答 */
    @PostMapping("/ask")
    public ResponseEntity<RAGAnswer> ask(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        Object tk = body.get("topK");
        int topK = tk instanceof Number ? ((Number) tk).intValue() : 3;
        return ResponseEntity.ok(ragService.answer(query, topK));
    }

    /** 软删除 */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Boolean> delete(@PathVariable Long id) {
        return ResponseEntity.ok(ragService.softDelete(id));
    }

    // ========== 知识库（问答对）管理 ==========
    // 📌 这组接口是给前端「AI 客服助手 → 知识库」页签用的。
    //    原来整个知识库管理**没有任何界面**，api 层写了函数但没视图调用，
    //    而服务端的知识又是内存 ArrayList —— 数据库里的种子数据没人读、
    //    新增的知识重启就丢。

    /**
     * 分页查询知识库条目（管理界面列表）
     *
     * 📌 返回体用的是**项目统一分页形状** {records, total, current, size, pages}，
     *    不是 Spring Data 的 Page 默认形状（content / totalElements）——
     *    前端的 unwrapPage() 和其他 20 多个列表页都按统一形状写的。
     */
    @GetMapping("/base/list")
    public ResponseEntity<Map<String, Object>> listBase(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "id"));
        Page<KnowledgeBase> result = (category == null || category.isBlank())
                ? knowledgeBaseRepository.findByActiveTrue(pageable)
                : knowledgeBaseRepository.findByCategoryAndActiveTrue(category, pageable);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("records", result.getContent());
        body.put("total", result.getTotalElements());
        body.put("current", page);
        body.put("size", safeSize);
        body.put("pages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    /** 所有分类（管理界面筛选用） */
    @GetMapping("/base/categories")
    public ResponseEntity<List<String>> listBaseCategories() {
        Set<String> categories = new LinkedHashSet<>();
        knowledgeBaseRepository.findByActiveTrue().forEach(k -> categories.add(k.getCategory()));
        return ResponseEntity.ok(List.copyOf(categories));
    }

    /** 删除知识条目（软删除：active=false） */
    @DeleteMapping("/base/{id}")
    public ResponseEntity<Boolean> deleteBase(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(id).orElse(null);
        if (kb == null) {
            return ResponseEntity.ok(false);
        }
        kb.setActive(false);
        knowledgeBaseRepository.save(kb);
        log.info("删除知识条目: id={}, category={}", id, kb.getCategory());
        return ResponseEntity.ok(true);
    }

    /**
     * 统计 —— <b>knowledge_base 表</b>（前端「知识库」页签管理、聊天检索用的就是这张表）
     *
     * <p>📌 **2026-11-04 修的真缺陷（口径不一致 → 界面自相矛盾）**：
     * <ul>
     *   <li>聊天检索走的是 {@code knowledge_base}：
     *       {@code AIAssistantServiceImpl.searchKnowledge()} →
     *       {@code knowledgeBaseRepository.findByActiveTrue()}；</li>
     *   <li>而这个接口原来返回 {@code ragService.stats()} →
     *       {@code knowledge_document} 表（<b>另一张表</b>）。</li>
     * </ul>
     * 结果：聊天页开场白写「基于 <b>5</b> 条行业知识库」，
     * 而同一个页面的「知识库」页签里列着 <b>31</b> 条 ——
     * <b>界面自己打自己的脸</b>（规则 19：界面不能承诺不存在的东西）。
     *
     * <p>→ 现在这个接口统计的表 = 聊天检索的表 = 前端管理的表，<b>三处口径一致</b>。
     *
     * <p>📌 保留 {@code RAGService.stats()}（knowledge_document 侧）是有意义的：
     * 那是向量文档的统计，与「行业知识库条数」是<b>两个不同的概念</b>，
     * 混用一个数字就会变成「A 的数字配 B 的文案」。
     */
    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStats> stats() {
        List<KnowledgeBase> all = knowledgeBaseRepository.findAll();
        long total = all.size();
        long active = all.stream().filter(k -> Boolean.TRUE.equals(k.getActive())).count();
        Map<String, Long> catCount = all.stream()
                .filter(k -> Boolean.TRUE.equals(k.getActive()))
                .filter(k -> k.getCategory() != null)
                .collect(Collectors.groupingBy(KnowledgeBase::getCategory, Collectors.counting()));
        Map<String, Long> langCount = all.stream()
                .filter(k -> Boolean.TRUE.equals(k.getActive()))
                .filter(k -> k.getLanguage() != null)
                .collect(Collectors.groupingBy(KnowledgeBase::getLanguage, Collectors.counting()));

        return ResponseEntity.ok(KnowledgeStats.builder()
                .totalCount(total)
                .activeCount(active)
                .categoryCount(catCount)
                .languageCount(langCount)
                .build());
    }

    /**
     * 统计 —— <b>knowledge_document 表</b>（RAG 向量文档侧）
     *
     * <p>📌 这是<b>另一个概念</b>，和上面的 {@code /stats}（knowledge_base）不能混：
     * <ul>
     *   <li>{@code /stats} → {@code knowledge_base}：问答对，<b>聊天检索用这张</b>；</li>
     *   <li>{@code /documents/stats} → {@code knowledge_document}：向量文档，
     *       {@code VectorSearchService} 用这张。</li>
     * </ul>
     * 📌 之前 {@code /stats} 返回的是 knowledge_document 的数字，
     * 而前端文案说的是「行业知识库」（= knowledge_base）→
     * <b>数字和文案说的是两张表</b>（规则 58：不要借用一个数字装另一种含义）。
     * → 现在两张表各有各的接口，<b>口径写在 URL 上</b>。
     */
    @GetMapping("/documents/stats")
    public ResponseEntity<KnowledgeStats> documentStats() {
        return ResponseEntity.ok(ragService.stats());
    }
}
