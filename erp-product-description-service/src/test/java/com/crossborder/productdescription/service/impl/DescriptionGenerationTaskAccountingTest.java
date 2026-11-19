package com.crossborder.productdescription.service.impl;

import com.crossborder.productdescription.config.LLMConfig;
import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.service.DescriptionGenerationTaskService;
import com.crossborder.productdescription.service.ProductCatalogService;
import com.crossborder.productdescription.service.ProductDescriptionRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 描述生成 → **任务只记一条** 的行为测试
 *
 * <p>📌 **要防的设计事故**：把记账写在「生成」里 →
 * 批量 5 条就会写 **5 条 SINGLE + 1 条 BATCH** = 6 行 → 统计对不上、页面乱。
 * → 现在记账只在**最外层入口**发生。</p>
 */
@DisplayName("描述生成的任务记账粒度（批量只记 1 条）")
class DescriptionGenerationTaskAccountingTest {

    private DescriptionGenerationTaskService taskService;
    private ProductDescriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        ProductDescriptionRecordService recordService = mock(ProductDescriptionRecordService.class);
        ProductCatalogService catalogService = mock(ProductCatalogService.class);
        taskService = mock(DescriptionGenerationTaskService.class);

        when(recordService.upsert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recordService.listByProductId(anyLong())).thenReturn(List.of());
        when(catalogService.findById(anyLong())).thenReturn(java.util.Optional.empty());
        when(catalogService.findBySku(anyString())).thenReturn(java.util.Optional.empty());

        // startTask 回显 taskId
        when(taskService.startTask(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(555L);
        // 统计：全部成功
        when(taskService.countSuccess(any())).thenReturn(3);

        service = new ProductDescriptionServiceImpl(
                new LLMConfig(), recordService, catalogService, taskService);
    }

    private DescriptionGenerationRequest req(Long id) {
        DescriptionGenerationRequest r = new DescriptionGenerationRequest();
        r.setProductId(id);
        r.setProductName("商品-" + id);
        r.setTargetLanguage("zh");
        return r;
    }

    @Test
    @DisplayName("批量生成 3 条 → 任务只开 1 次（BATCH），不是 3 次")
    void batchGeneratesExactlyOneTask() {
        service.batchGenerateDescription(List.of(req(1L), req(2L), req(3L)));

        // 📌 关键断言：**只开一次**任务
        verify(taskService, times(1)).startTask(
                eq(DescriptionGenerationTaskService.TASK_TYPE_BATCH),
                eq(null), eq(null), eq(null), eq(3));
        verify(taskService, times(1)).completeTask(eq(555L), eq(3), eq(0), any(Long.class));
    }

    @Test
    @DisplayName("单条生成 → 记 1 条 SINGLE")
    void singleGeneratesOneSingleTask() {
        service.generateDescription(req(9L));

        verify(taskService, times(1)).startTask(
                eq(DescriptionGenerationTaskService.TASK_TYPE_SINGLE),
                eq(9L), eq("zh"), any(), eq(1));
    }

    @Test
    @DisplayName("A/B 生成 2 版 → 记 1 条 AB_TEST（不是 2 条 SINGLE）")
    void abTestGeneratesOneTask() {
        service.generateABTestVersions(5L, 2);

        verify(taskService, times(1)).startTask(
                eq(DescriptionGenerationTaskService.TASK_TYPE_AB_TEST),
                eq(5L), eq(null), eq(null), eq(2));
    }
}
