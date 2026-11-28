package com.crossborder.productdescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开一个 A/B 测试的请求
 *
 * <p>📌 <b>为什么要有这个 DTO</b>：表 {@code ab_test_result} 的 DDL 里
 * 已经把「获胜版本（A、B、TIE）」「置信度」「测试天数」这些字段设计好了，
 * 但<b>全仓没有任何代码往里写</b> → 表永远是空的 →
 * 运营问「A 版和 B 版哪个 CTR 高」→ <b>表里没数据，问不出来</b>。</p>
 *
 * <p>📌 <b>开测时指标值一定是 null</b>：CTR / conversion_rate 要等真实流量跑出来才有
 * → 本请求<b>不收</b>指标值（收到也会被忽略），避免调用方以为「填了就算数」。</p>
 */
@Data
public class AbTestStartRequest {

    /** 商品ID（NOT NULL） */
    @NotNull(message = "产品ID不能为空")
    private Long productId;

    /** 测试名称（NOT NULL） */
    @NotBlank(message = "测试名称不能为空")
    private String testName;

    /** 指标名称（NOT NULL，如 CTR / conversion_rate） */
    @NotBlank(message = "指标名称不能为空")
    private String metricName;

    /** 版本A描述ID（关联 {@code product_description.id}） */
    private Long versionAId;

    /** 版本B描述ID（关联 {@code product_description.id}） */
    private Long versionBId;

    /** 测试开始时间（NOT NULL）；不传就是「现在」 */
    private LocalDateTime startTime;
}
