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
 * SEO 优化结果实体（表 {@code seo_optimization}）
 *
 * <p>📌 <b>2026-11-28 新增</b>：这张表<b>早就建好了 DDL</b>
 * （{@code database/product-description-service.sql} 第 69 行），
 * 但<b>全仓没有任何一行代码往里写</b> →
 * {@code optimizeSEO()} 每次都<b>现算一遍就丢掉</b> →
 * 运营问「上周那条文案当时打了几分 SEO？」→ <b>查不到</b>，
 * 因为答案只活在一次 HTTP 响应的内存里。</p>
 *
 * <p>📌 <b>字段与列的对应</b>：MyBatis-Plus 默认驼峰转下划线
 * （{@code primaryKeywords → primary_keywords}），
 * 与本模块 {@link ProductDescription} / {@link DescriptionTemplate} 一致。</p>
 *
 * <p>📌 <b>JSON 列用 {@code String} 承载</b>：
 * {@code primary_keywords} / {@code long_tail_keywords} / {@code keyword_density} /
 * {@code suggestions} 四列在 DDL 里都是 MySQL {@code JSON} 类型 ——
 * Java 侧用 {@code String} 序列化进去，避免 MySQL 对 JSON 列做严格校验时报
 * {@code "Invalid JSON text"}。与本模块 {@code description_template.variables} 同口径。</p>
 *
 * <p>📌 <b>为什么 {@code createTime} 标 {@code fill}</b>：
 * {@code erp-common} 的 {@code MybatisPlusConfig} 注册了 {@code MetaObjectHandler}，
 * 全项目统一自动填充审计时间。</p>
 */
@Data
@TableName("seo_optimization")
public class SeoOptimization {

    /** SEO 优化ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 描述ID（关联 {@code product_description.id}） */
    private Long descriptionId;

    /** 产品ID */
    private Long productId;

    /** 优化后的标题 */
    private String optimizedTitle;

    /** 优化后的描述 */
    private String optimizedDescription;

    /** 主要关键词（JSON 数组字符串，如 {@code ["降噪","续航"]}） */
    private String primaryKeywords;

    /** 长尾关键词（JSON 数组字符串） */
    private String longTailKeywords;

    /** 关键词密度（JSON 对象字符串，如 {@code {"降噪":0.05}}） */
    private String keywordDensity;

    /** 可读性评分（0-100） */
    private BigDecimal readabilityScore;

    /** SEO 评分（0-100） */
    private BigDecimal seoScore;

    /** 改进建议（JSON 数组字符串） */
    private String suggestions;

    /** 优化时长（毫秒） */
    private Long optimizeTimeMs;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
