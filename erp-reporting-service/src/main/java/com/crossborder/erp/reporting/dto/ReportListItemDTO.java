package com.crossborder.erp.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 报表列表项
 *
 * <p>前端 erp-web/src/views/report/index.vue 需要这张表：id / name / type /
 * period / generatedAt / status。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportListItemDTO {

    /** 报表 ID */
    private Long id;

    /** 报表名称 */
    private String name;

    /** 报表类型：sales / inventory / finance */
    private String type;

    /** 统计周期（报表日期） */
    private String period;

    /** 周期粒度：DAILY / WEEKLY / MONTHLY */
    private String granularity;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 状态 */
    private String status;
}
