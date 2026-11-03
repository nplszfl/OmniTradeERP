package com.crossborder.tenant.dto.dashboard;

import lombok.*;
import java.time.LocalDate;

/**
 * 用量趋势（数据粒度 = 计费周期/月）
 *
 * <p>📌 之前这个 DTO 只有 date 字段，后端却用 Math.random() 造「最近 7 天」，
 * 页面上的趋势图每次刷新都不同。现在改成读真实的月度用量，
 * 所以 date 取**周期首日**，并补一个 period 字段直接展示 'yyyy-MM'。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyUsageDTO {

    private LocalDate date;

    /** 计费周期（yyyy-MM），直接展示用 */
    private String period;

    private Long apiCalls;
    private Integer orders;
    private Long storageBytes;
}