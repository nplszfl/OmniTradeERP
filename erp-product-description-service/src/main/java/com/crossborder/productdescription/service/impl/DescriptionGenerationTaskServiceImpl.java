package com.crossborder.productdescription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.entity.DescriptionGenerationTask;
import com.crossborder.productdescription.mapper.DescriptionGenerationTaskMapper;
import com.crossborder.productdescription.service.DescriptionGenerationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 描述生成任务审计服务实现
 *
 * <p>📌 **落库失败不能影响主流程**：任务审计是「旁路记账」，
 * 记账本身出错不该让用户的描述生成失败 → 内部 catch + 记日志（规则 18 反面：
 * 事实标志不能撒谎，但**旁路记账失败不该拖垮主业务**）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DescriptionGenerationTaskServiceImpl implements DescriptionGenerationTaskService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final DescriptionGenerationTaskMapper taskMapper;

    @Override
    public Long startTask(String taskType, Long productId, String targetLanguage,
                          String targetPlatform, int totalCount) {
        try {
            DescriptionGenerationTask task = new DescriptionGenerationTask();
            task.setTaskType(taskType);
            task.setProductId(productId);
            task.setTargetLanguage(targetLanguage);
            task.setTargetPlatform(targetPlatform);
            task.setStatus(STATUS_RUNNING);
            task.setTotalCount(totalCount);
            task.setSuccessCount(0);
            task.setFailedCount(0);
            task.setStartTime(LocalDateTime.now());
            task.setCreateTime(LocalDateTime.now());
            taskMapper.insert(task);
            return task.getId();
        } catch (Exception e) {
            log.warn("⚠️ 描述生成任务**开始**记录失败（不影响生成）: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void completeTask(Long taskId, int successCount, int failedCount, long durationMs) {
        if (taskId == null) {
            return;
        }
        try {
            DescriptionGenerationTask task = new DescriptionGenerationTask();
            task.setId(taskId);
            task.setStatus(STATUS_SUCCESS);
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setDurationMs(durationMs);
            task.setEndTime(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("⚠️ 描述生成任务**完成**记录失败（taskId={}）: {}", taskId, e.getMessage());
        }
    }

    @Override
    public void failTask(Long taskId, int successCount, int failedCount, long durationMs, String errorMessage) {
        if (taskId == null) {
            return;
        }
        try {
            DescriptionGenerationTask task = new DescriptionGenerationTask();
            task.setId(taskId);
            task.setStatus(STATUS_FAILED);
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setDurationMs(durationMs);
            task.setErrorMessage(truncate(errorMessage, 2000));
            task.setEndTime(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("⚠️ 描述生成任务**失败**记录失败（taskId={}）: {}", taskId, e.getMessage());
        }
    }

    @Override
    public int countSuccess(List<DescriptionGenerationResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return 0;
        }
        int success = 0;
        for (DescriptionGenerationResponse r : responses) {
            if (r == null) {
                continue;
            }
            // 📌 失败判据 = **有 error 字段**；只看「content 非空」会把失败算成成功
            boolean failed = r.getError() != null && !r.getError().isBlank();
            if (!failed) {
                success++;
            }
        }
        return success;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DescriptionGenerationTask> listRecentByProduct(Long productId, int limit) {
        QueryWrapper<DescriptionGenerationTask> qw = new QueryWrapper<>();
        qw.eq("product_id", productId)
          .orderByDesc("create_time")
          .last("LIMIT " + Math.max(1, limit));
        return taskMapper.selectList(qw);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
