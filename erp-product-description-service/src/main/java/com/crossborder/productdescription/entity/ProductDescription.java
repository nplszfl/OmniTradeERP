package com.crossborder.productdescription.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品描述实体（表 {@code product_description}）
 *
 * <p>📌 <b>这个类存在的意义</b>：本模块之前<b>一个持久层都没有</b> ——
 * {@code saveDescription} 只打了一行日志就返回 {@code true}，
 * {@code getProductDescriptions} 返回的是<b>写死的假数据</b>
 * （"这是一个高质量的产品，具有优秀的性能和可靠性..."）。
 * 表在 {@code database/product-description-service.sql} 里早就建好了，
 * 唯一键 {@code uk_pd_product_lang_platform (product_id, language, platform)} 也在，
 * 就差一个能落库的 Mapper。</p>
 *
 * <p>📌 <b>字段与列的对应</b>：MyBatis-Plus 默认把驼峰转下划线
 * （{@code versionNumber → version_number}），
 * 与本仓库 {@code erp-pricing-service} 的 {@code PriceHistory} 实体写法一致。</p>
 *
 * <p>📌 <b>为什么 {@code createTime/updateTime} 标 {@code fill}</b>：
 * {@code erp-common} 的 {@code MybatisPlusConfig} 注册了 {@code MetaObjectHandler}，
 * 全项目统一自动填充审计时间；不标的话这两个字段永远是 null
 * （历史遗留问题：几十个实体标了 fill 注解，但 handler 从来没注册过 → 全是摆设）。</p>
 */
@Data
@TableName("product_description")
public class ProductDescription {

    /** 描述ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品ID（雪花 ID，19 位） */
    private Long productId;

    /** 产品名称 */
    private String productName;

    /** 描述正文 */
    private String content;

    /** 语言（zh / en / ja 等） */
    private String language;

    /** 目标平台（amazon / ebay / shopee / general） */
    private String platform;

    /** 版本号：同一 (product_id, language, platform) 重复保存时递增 */
    private Integer versionNumber;

    /** 短标题 */
    private String shortTitle;

    /** 使用的模板ID */
    private String templateId;

    /** 使用的AI模型（mock 表示本地模板生成，不是真 AI） */
    private String model;

    /** 状态（GENERATED / APPROVED / PUBLISHED） */
    private String status;

    /** 消耗的 token 数 */
    private Integer tokensUsed;

    /** 生成耗时（毫秒） */
    private Long generationTimeMs;

    /** 审批人 */
    private String approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedTime;

    /** 发布时间 */
    private LocalDateTime publishedTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
