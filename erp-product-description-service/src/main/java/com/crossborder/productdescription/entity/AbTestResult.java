package com.crossborder.productdescription.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A/B 测试结果实体（表 {@code ab_test_result}）
 *
 * <p>📌 <b>2026-11-28 新增</b>：这张表<b>早就建好了 DDL</b>
 * （{@code database/product-description-service.sql} 第 124 行），
 * 但<b>全仓没有任何一行代码往里写</b> →
 * {@code generateABTestVersions()} 每次都<b>现生成几个版本就丢掉</b> →
 * 运营想「上周那条文案 A 版和 B 版哪个 CTR 高？」→ <b>表里没数据，问不出来</b>，
 * 而 DDL 的注释里明确写了「获胜版本（A、B、TIE）」和「置信度」——
 * <b>连字段该怎么填都设计好了，却没有任何代码去填</b>。</p>
 *
 * <p>📌 <b>字段与列的对应</b>：MyBatis-Plus 默认驼峰转下划线
 * （{@code metricAValue → metric_a_value}），
 * 与本模块 {@link ProductDescription} / {@link DescriptionTemplate} 一致。</p>
 *
 * <p>📌 <b>为什么 {@code startTime} 是 NOT NULL 而 {@code endTime} 可空</b>：
 * 测试「开始」是事实，「结束」要等跑完才知道 → 允许 null（= 还在跑）。</p>
 */
@Data
@TableName("ab_test_result")
public class AbTestResult {

    /** 测试ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品ID */
    private Long productId;

    /** 测试名称 */
    private String testName;

    /** 版本A描述ID（关联 {@code product_description.id}） */
    private Long versionAId;

    /** 版本B描述ID（关联 {@code product_description.id}） */
    private Long versionBId;

    /** 指标名称（CTR、conversion_rate 等） */
    private String metricName;

    /** 版本A指标值 */
    private BigDecimal metricAValue;

    /** 版本B指标值 */
    private BigDecimal metricBValue;

    /** 获胜版本（A / B / TIE） */
    private String winner;

    /** 置信度（0-1） */
    private BigDecimal confidence;

    /** 测试天数 */
    private Integer testDurationDays;

    /** 开始时间（NOT NULL） */
    private LocalDateTime startTime;

    /** 结束时间（还在跑 → null） */
    private LocalDateTime endTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
