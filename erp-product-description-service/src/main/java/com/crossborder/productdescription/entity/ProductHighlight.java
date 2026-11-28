package com.crossborder.productdescription.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品亮点实体（表 {@code product_highlights}）
 *
 * <p>📌 <b>2026-11-28 新增</b>：这张表<b>早就建好了 DDL</b>
 * （{@code database/product-description-service.sql} 第 89 行），
 * 但<b>全仓没有任何一行代码往里写</b> →
 * {@code extractHighlights()} 每次都<b>现算一遍就丢掉</b> →
 * 运营问「这条商品上次提炼的亮点是什么？」→ <b>查不到</b>。</p>
 *
 * <p>📌 <b>字段与列的对应</b>：MyBatis-Plus 默认驼峰转下划线
 * （{@code highlightTitle → highlight_title}），
 * 与本模块 {@link ProductDescription} / {@link DescriptionTemplate} 一致。</p>
 *
 * <p>📌 <b>{@code highlight_title} 是 NOT NULL</b> → 写路径必须保证有值：
 * 提炼出来的亮点<b>标题</b>来自真实商品特性（不是「核心优势 1」这种占位标题），
 * 拿不到真实特性就<b>不写这行</b>，绝不编标题凑数。</p>
 */
@Data
@TableName("product_highlights")
public class ProductHighlight {

    /** 亮点ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 描述ID（关联 {@code product_description.id}） */
    private Long descriptionId;

    /** 产品ID */
    private Long productId;

    /** 亮点标题（NOT NULL） */
    private String highlightTitle;

    /** 亮点描述 */
    private String highlightDescription;

    /** 对应的产品特性 */
    private String feature;

    /** 重要性（1-5） */
    private Integer importance;

    /** 排序 */
    private Integer orderNumber;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
