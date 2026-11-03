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

    /** 统计 */
    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStats> stats() {
        return ResponseEntity.ok(ragService.stats());
    }
}
