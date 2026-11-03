package com.crossborder.aiassistant.controller;

import com.crossborder.aiassistant.entity.KnowledgeBase;
import com.crossborder.aiassistant.repository.KnowledgeBaseRepository;
import com.crossborder.aiassistant.service.RAGService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库管理接口单元测试
 *
 * 📌 锁的是 2026-11-03 补上的这一组接口：
 *    之前知识库**没有任何管理界面**，api 层有函数但没有视图调用，
 *    服务端的知识还是内存 ArrayList —— 数据库里的种子数据没人读、
 *    新增的知识重启就丢。
 */
class KnowledgeBaseControllerTest {

    private KnowledgeController controller;
    private KnowledgeBaseRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(KnowledgeBaseRepository.class);
        controller = new KnowledgeController(mock(RAGService.class), repository);
    }

    private KnowledgeBase item(long id, String category) {
        return KnowledgeBase.builder()
                .id(id).category(category).question("问题" + id).answer("答案" + id)
                .language("zh").tags(List.of("标签")).priority(5).active(true).build();
    }

    @Test
    @DisplayName("知识库列表返回统一分页形状（records/total/current/size/pages）")
    void listBaseReturnsUnifiedPageShape() {
        List<KnowledgeBase> rows = List.of(item(1L, "订单"), item(2L, "物流"));
        when(repository.findByActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 2));

        Map<String, Object> body = controller.listBase(1, 20, null).getBody();

        assertEquals(2L, ((Number) body.get("total")).longValue());
        assertEquals(1, body.get("current"));
        assertEquals(20, body.get("size"));
        assertEquals(1, body.get("pages"));
        assertTrue(body.get("records") instanceof List);
        assertEquals(2, ((List<?>) body.get("records")).size());
    }

    @Test
    @DisplayName("按分类过滤时走分类查询")
    void listBaseFiltersByCategory() {
        when(repository.findByCategoryAndActiveTrue(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item(3L, "售后")), PageRequest.of(0, 20), 1));

        Map<String, Object> body = controller.listBase(1, 20, "售后").getBody();

        assertEquals(1L, ((Number) body.get("total")).longValue());
        verify(repository).findByCategoryAndActiveTrue(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("分类列表去重后返回")
    void listCategoriesDeduplicates() {
        when(repository.findByActiveTrue()).thenReturn(List.of(
                item(1L, "订单"), item(2L, "订单"), item(3L, "物流")));

        List<String> categories = controller.listBaseCategories().getBody();

        assertEquals(2, categories.size());
        assertTrue(categories.contains("订单"));
        assertTrue(categories.contains("物流"));
    }

    @Test
    @DisplayName("删除走软删除（active=false），记录仍保留")
    void deleteBaseSoftDeletes() {
        KnowledgeBase row = item(9L, "物流");
        when(repository.findById(9L)).thenReturn(java.util.Optional.of(row));

        Boolean ok = controller.deleteBase(9L).getBody();

        assertTrue(ok);
        assertFalse(row.getActive());
        verify(repository).save(row);
    }

    @Test
    @DisplayName("删除不存在的条目返回 false，不抛异常")
    void deleteBaseMissingReturnsFalse() {
        when(repository.findById(404L)).thenReturn(java.util.Optional.empty());

        Boolean ok = controller.deleteBase(404L).getBody();

        assertFalse(ok);
    }
}
