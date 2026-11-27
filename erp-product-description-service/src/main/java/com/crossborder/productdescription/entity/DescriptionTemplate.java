package com.crossborder.productdescription.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 描述模板实体（表 {@code description_template}）
 *
 * <p>📌 <b>2026-11-24 新增</b>：这张表<b>早就建好了 DDL</b>
 * （{@code database/product-description-service.sql}），
 * 而且<b>种子数据都种进去了</b>（{@code amazon_template} / {@code ebay_template} /
 * {@code shopee_template} / {@code general_template} 四条），
 * 但<b>全仓没有任何一行代码读它</b> → 表里的模板内容是「装饰品」。
 *
 * <p>🔴 <b>修它之前，这个模块里的模板是「假的」</b>：
 * <ul>
 *   <li>{@code ProductDescriptionServiceImpl} 用一个<b>内存 Map</b>
 *       {@code templates = {"amazon" → "amazon_product_template", ...}}
 *       硬编码模板 ID —— 而库里真实存在的是 {@code amazon_template}，
 *       <b>两套 ID 对不上</b>；</li>
 *   <li>{@code listTemplates()} 返回的是<b>写死的模板元数据</b>，
 *       根本没查库；</li>
 *   <li>{@code buildPrompt()} <b>从头到尾没用过模板内容</b>
 *       —— {@code useTemplate} / {@code templateId} 两个入参是<b>死参数</b>，
 *       运营在后台改模板对生成结果<b>零影响</b>。</li>
 * </ul>
 *
 * <p>📌 <b>字段与列的对应</b>：MyBatis-Plus 默认驼峰转下划线
 * （{@code templateId → template_id}），与本模块 {@code ProductDescription} 一致。
 *
 * <p>📌 <b>{@code variables} 列是 MySQL {@code JSON} 类型</b> ——
 * Java 侧用 {@code String} 承载（序列化成 JSON 字符串），
 * 避免 MySQL 对 JSON 列做严格校验时报
 * {@code "Invalid JSON text"}。
 */
@Data
@TableName("description_template")
public class DescriptionTemplate {

    /** 模板ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板标识（业务唯一键，如 {@code amazon_template}） */
    private String templateId;

    /** 模板名称 */
    private String name;

    /** 目标平台（Amazon / eBay / Shopee / general） */
    private String platform;

    /** 语言（zh / en …） */
    private String language;

    /** 模板正文（含 {@code {变量名}} 占位符） */
    private String templateContent;

    /** 模板变量（JSON 数组字符串，如 {@code ["产品名称","产品特性"]}） */
    private String variables;

    /** 最大长度 */
    private Integer maxLength;

    /** 是否启用（1 启用 / 0 停用） */
    private Integer enabled;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
