package com.crossborder.pricing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定价任务实体
 *
 * <p>对应表 {@code pricing_task}（见 database/pricing-service.sql）。</p>
 * <p>记录定时抓取 / 动态定价的执行情况。</p>
 */
@Data
@TableName("pricing_task")
public class PricingTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类型（COMPETITOR_SCRAPE、DYNAMIC_PRICING 等） */
    private String taskType;

    /** 产品ID（NULL 表示批量任务） */
    private Long productId;

    /** 状态（PENDING、RUNNING、SUCCESS、FAILED） */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 结果描述 */
    private String resultText;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;
}
