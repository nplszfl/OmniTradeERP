package com.crossborder.productdescription.service.impl;

import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.entity.DescriptionGenerationTask;
import com.crossborder.productdescription.mapper.DescriptionGenerationTaskMapper;
import com.crossborder.productdescription.service.DescriptionGenerationTaskService;
import com.crossborder.productdescription.service.impl.DescriptionGenerationTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 描述生成任务审计（写路径）单元测试
 *
 * <p>📌 重点验证：表 {@code description_generation_task} **之前有 DDL、没代码 → 永远 0 行**，
 * 现在补上写路径，就要证明：
 * <ol>
 *   <li>任务真的**开始/结束**都落库（RUNNING → SUCCESS）；</li>
 *   <li><b>批量只记 1 条</b>（不是 N+1 条）——否则统计对不上；</li>
 *   <li>失败条数<b>如实</b>计入（不能把失败算成成功）；</li>
 *   <li>失败时任务 status = FAILED 且**写了错误信息**（不吞）。</li>
 * </ol>
 */
@DisplayName("DescriptionGenerationTaskService 写路径单元测试")
class DescriptionGenerationTaskServiceImplTest {

    private DescriptionGenerationTaskMapper taskMapper;
    private DescriptionGenerationTaskService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(DescriptionGenerationTaskMapper.class);
        // insert 回显自增主键（模拟 DB 行为）
        when(taskMapper.insert(any(DescriptionGenerationTask.class))).thenAnswer(inv -> {
            DescriptionGenerationTask t = inv.getArgument(0);
            t.setId(1001L);
            return 1;
        });
        service = new DescriptionGenerationTaskServiceImpl(taskMapper);
    }

    private DescriptionGenerationResponse resp(String error) {
        DescriptionGenerationResponse r = new DescriptionGenerationResponse();
        r.setContent("一段描述");
        r.setError(error);
        return r;
    }

    @Test
    @DisplayName("开始任务 → 真的写一行（status=RUNNING，总数=计划条数）")
    void startTaskWritesRunningRow() {
        Long id = service.startTask(DescriptionGenerationTaskService.TASK_TYPE_BATCH,
                null, null, null, 5);

        ArgumentCaptor<DescriptionGenerationTask> captor = ArgumentCaptor.forClass(DescriptionGenerationTask.class);
        verify(taskMapper, times(1)).insert(captor.capture());
        DescriptionGenerationTask t = captor.getValue();
        assertEquals("RUNNING", t.getStatus());
        assertEquals("BATCH", t.getTaskType());
        assertEquals(5, t.getTotalCount());
        assertEquals(0, t.getSuccessCount());
        assertEquals(1001L, id);
    }

    @Test
    @DisplayName("完成任务 → 落 SUCCESS + 成功/失败条数 + 耗时")
    void completeTaskWritesSuccess() {
        service.startTask(DescriptionGenerationTaskService.TASK_TYPE_BATCH, null, null, null, 3);
        service.completeTask(1001L, 2, 1, 1234L);

        ArgumentCaptor<DescriptionGenerationTask> captor = ArgumentCaptor.forClass(DescriptionGenerationTask.class);
        verify(taskMapper, times(1)).updateById(captor.capture());
        DescriptionGenerationTask t = captor.getValue();
        assertEquals("SUCCESS", t.getStatus());
        assertEquals(2, t.getSuccessCount());
        assertEquals(1, t.getFailedCount());
        assertEquals(1234L, t.getDurationMs());
        assertTrue(t.getEndTime() != null);
    }

    @Test
    @DisplayName("失败任务 → 落 FAILED + 错误信息（如实写，不吞）")
    void failTaskWritesErrorMessage() {
        service.startTask(DescriptionGenerationTaskService.TASK_TYPE_SINGLE, 7L, "en", "amazon", 1);
        service.failTask(1001L, 0, 1, 55L, "LLM 401 unauthorized");

        ArgumentCaptor<DescriptionGenerationTask> captor = ArgumentCaptor.forClass(DescriptionGenerationTask.class);
        verify(taskMapper, times(1)).updateById(captor.capture());
        DescriptionGenerationTask t = captor.getValue();
        assertEquals("FAILED", t.getStatus());
        assertEquals(1, t.getFailedCount());
        assertEquals("LLM 401 unauthorized", t.getErrorMessage());
    }

    @Test
    @DisplayName("成功条数统计：有 error 的算失败 —— 不能只看「content 非空」")
    void countSuccessTreatsErrorAsFailure() {
        List<DescriptionGenerationResponse> responses = List.of(
                resp(null), resp(null),
                resp("生成失败，请稍后重试。"),
                resp("boom"));
        assertEquals(2, service.countSuccess(responses));
    }

    @Test
    @DisplayName("空列表 → 0 条（不抛异常、不返回 -1）")
    void countSuccessOnEmptyList() {
        assertEquals(0, service.countSuccess(List.of()));
        assertEquals(0, service.countSuccess(null));
    }

    @Test
    @DisplayName("落库失败不影响主流程（旁路记账）→ startTask 返回 null 而不抛异常")
    void taskAccountingFailureDoesNotBreakMainFlow() {
        when(taskMapper.insert(any(DescriptionGenerationTask.class)))
                .thenThrow(new RuntimeException("MyBatis 连不上"));
        Long id = service.startTask(DescriptionGenerationTaskService.TASK_TYPE_SINGLE, 1L, null, null, 1);
        assertEquals(null, id);
        // completeTask(null) 也不能炸
        service.completeTask(null, 1, 0, 10L);
    }
}
