package com.crossborder.erp.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 报表生成请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportGenerateRequest {

    /** 报表类型：sales / inventory / finance */
    @NotBlank(message = "报表类型不能为空")
    private String type;

    /** 统计日期，默认为今天 */
    private LocalDate date;

    /** 平台（可选） */
    private String platform;

    /** 店铺（可选） */
    private String storeId;
}
