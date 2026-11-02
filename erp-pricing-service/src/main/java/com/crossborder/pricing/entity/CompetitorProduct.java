package com.crossborder.pricing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 竞品数据实体
 *
 * <p>⚠️ 表名与列名都跟 database/pricing-service.sql 的 DDL 对齐过：
 * 实体原来写的是 {@code @TableName("t_competitor_product")}，
 * 但 DDL 建的是 {@code competitor_product}（没有 t_ 前缀）——
 * 一旦有代码真的调 {@link com.crossborder.pricing.mapper.CompetitorProductMapper}，
 * 就会抛 "Table 'erp_pricing.t_competitor_product' doesn't exist"。
 *
 * <p>另外 DDL 里只有一列 {@code price}，实体原来有
 * {@code currentPrice} / {@code lowestPrice} / {@code highestPrice} 三个字段
 * —— 后两个在 DDL 里根本没有对应列。MyBatis-Plus 驼峰转下划线会去找
 * {@code current_price} / {@code lowest_price} / {@code highest_price}，全部落空。
 *
 * <p>现在的处理：
 * <ul>
 *   <li>{@code currentPrice} 显式映射到 {@code price}（Java 侧 API 不变，
 *       {@code getCurrentPrice()} 那些调用点不用动）</li>
 *   <li>{@code lowestPrice} / {@code highestPrice} 标 {@code exist = false} ——
 *       DDL 里没有这两列，不参与 SQL 生成；由
 *       {@code CompetitorScrapeServiceImpl} 从历史价表自行填充</li>
 * </ul></p>
 */
@Data
@TableName("competitor_product")
public class CompetitorProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品ID */
    private Long productId;

    /** 竞品名称 */
    private String competitorName;

    /** 当前价格 —— DDL 列名是 {@code price}，这里显式映射 */
    @TableField("price")
    private BigDecimal currentPrice;

    /** 历史低价 —— DDL 里没有这一列，不参与 SQL（由服务层从历史价填充） */
    @TableField(exist = false)
    private BigDecimal lowestPrice;

    /** 历史高价 —— DDL 里没有这一列，不参与 SQL（由服务层从历史价填充） */
    @TableField(exist = false)
    private BigDecimal highestPrice;

    /** 竞品URL */
    private String productUrl;

    /** 平台（Amazon、eBay、Shopee等） */
    private String platform;

    /** 评分（1-5） */
    private BigDecimal rating;

    /** 评论数 */
    private Long reviewCount;

    /** 销售量 */
    private Long salesVolume;

    /** 抓取时间 */
    private LocalDateTime scrapeTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
