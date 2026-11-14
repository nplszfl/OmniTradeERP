package com.crossborder.pricing.service;

import com.crossborder.pricing.entity.CompetitorProduct;

import java.math.BigDecimal;
import java.util.List;

/**
 * 竞品数据抓取服务
 *
 * <p>功能：
 * <ol>
 *   <li>从Amazon、eBay、Shopee等平台<b>真抓取</b>竞品价格（OkHttp + Jsoup）；</li>
 *   <li>解析HTML获取产品信息（价格、评分、评论数）；</li>
 *   <li><b>存储到数据库</b>（{@code competitor_product} 表，自然唯一键 upsert）；</li>
 *   <li>提供竞品分析数据。</li>
 * </ol>
 */
public interface CompetitorScrapeService {

    /**
     * 抓取竞品数据（覆盖 Amazon / eBay / Shopee / Lazada 四个平台）
     *
     * @throws com.crossborder.erp.common.exception.BizException 产品不存在，或所有平台都抓取失败
     */
    void scrapeCompetitorProducts(Long productId);

    /**
     * 从指定平台抓取竞品数据。
     *
     * <p>📌 **2026-11-14 签名变更**：原来返回 {@code void}，抓到的数据被
     * {@code log.debug} 丢掉 → <b>一个字节都没落库</b>（假成功）。
     * 现在返回解析出的实体，由调用方决定是否入库。</p>
     *
     * @return 解析出的竞品数据（价格解析不出来时返回 {@code null}）
     * @throws com.crossborder.erp.common.exception.BizException 参数非法或抓取失败
     */
    CompetitorProduct scrapeFromPlatform(String platform, String productUrl);

    /**
     * 批量抓取多个产品的竞品数据。
     *
     * <p>单个产品失败不影响整个批次；全部失败时抛异常（不能静默成功）。</p>
     */
    void batchScrape(List<Long> productIds);

    /**
     * 获取产品的竞品数据（<b>查数据库</b>，不是现场生成）
     */
    List<CompetitorProduct> getCompetitorProducts(Long productId);

    /**
     * 获取竞品价格统计（平均、最低、最高）
     */
    CompetitorPriceStats getCompetitorPriceStats(Long productId);

    /**
     * 手动添加竞品（<b>真落库</b>）
     */
    void addCompetitor(Long productId, CompetitorProduct competitor);

    /**
     *   竞品价格统计
     */
    class CompetitorPriceStats {
        private Long productId;
        private BigDecimal avgPrice;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Long competitorCount;

        // Getters and Setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public BigDecimal getAvgPrice() { return avgPrice; }
        public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }

        public BigDecimal getMinPrice() { return minPrice; }
        public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

        public BigDecimal getMaxPrice() { return maxPrice; }
        public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

        public Long getCompetitorCount() { return competitorCount; }
        public void setCompetitorCount(Long competitorCount) { this.competitorCount = competitorCount; }
    }
}
