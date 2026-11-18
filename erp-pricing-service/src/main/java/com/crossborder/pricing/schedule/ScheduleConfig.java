package com.crossborder.pricing.schedule;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.entity.Product;
import com.crossborder.pricing.mapper.ProductMapper;
import com.crossborder.pricing.service.AiPricingSuggestionGenerator;
import com.crossborder.pricing.service.CompetitorScrapeService;
import com.crossborder.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * 定时任务配置
 *
 * <p>📌 **2026-11-14 修复**：{@code getMonitoredProducts()} 原来返回
 * {@code List.of(1L, 2L, 3L, 4L, 5L)} —— <b>写死的 5 个 ID</b>，
 * 注释写着「从数据库查询（模拟）」。
 * → 商品表里没有这几个 ID（比如 ID 是雪花算法生成的）→ <b>定时任务每轮都在抓空气</b>，
 * 而且日志会说「没有需要监控的产品」，看起来一切正常。
 * → 现在真查商品表（只取上架状态的）。
 */
@Configuration
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class ScheduleConfig {

    private final CompetitorScrapeService competitorScrapeService;
    private final PricingService pricingService;
    private final ProductMapper productMapper;
    private final AiPricingSuggestionGenerator aiPricingSuggestionGenerator;

    /**
     * 竞品数据抓取定时任务
     * 每小时执行一次
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // 60分钟
    public void scrapeCompetitorProductsSchedule() {
        log.info("🕐 定时任务：开始抓取竞品数据");

        try {
            List<Long> productIds = getMonitoredProducts();

            if (!productIds.isEmpty()) {
                competitorScrapeService.batchScrape(productIds);
                log.info("✅ 竞品数据抓取完成 - 产品数量: {}", productIds.size());
            } else {
                log.debug("没有需要监控的产品");
            }

        } catch (Exception e) {
            log.error("❌ 竞品数据抓取失败", e);
        }
    }

    /**
     * 动态定价定时任务
     * 每30分钟执行一次
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30分钟
    public void dynamicPricingSchedule() {
        log.info("🕐 定时任务：开始动态定价调整");

        try {
            List<Long> productIds = getMonitoredProducts();

            int adjustedCount = 0;
            for (Long productId : productIds) {
                try {
                    pricingService.adjustPriceByCompetitors(productId);
                    adjustedCount++;
                } catch (Exception e) {
                    log.error("产品 {} 价格调整失败", productId, e);
                }
            }

            log.info("✅ 动态定价完成 - 调整数量: {}", adjustedCount);

        } catch (Exception e) {
            log.error("❌ 动态定价失败", e);
        }
    }

    /**
     * 定价建议生成定时任务（每 30 分钟）
     *
     * <p>📌 **2026-11-18 新增** —— 补上 <b>缺失的那条写路径</b>：
     * {@code ai_pricing_suggestion} 表以前<b>从来没人往里写</b>（活库里 0 行）
     * → 前端「AI 定价建议」页永远空。
     * <p>📌 建议数字 = 确定性规则；建议原因 = LLM 生成（不可用时回落规则模板并如实标注）。
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30分钟
    public void pricingSuggestionSchedule() {
        log.info("🕐 定时任务：开始生成定价建议");
        try {
            List<Long> productIds = getMonitoredProducts();
            int generated = aiPricingSuggestionGenerator.generateForProducts(productIds);
            log.info("✅ 定价建议生成完成 - 生成条数: {}", generated);
        } catch (Exception e) {
            log.error("❌ 定价建议生成失败", e);
        }
    }

    /**
     * 获取需要监控的产品 —— <b>真查商品表</b>（上架状态）。
     *
     * <p>📌 原来返回写死的 {@code List.of(1L, 2L, 3L, 4L, 5L)}。</p>
     */
    private List<Long> getMonitoredProducts() {
        List<Product> products = productMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)
                        .orderByAsc(Product::getId)
                        .last("LIMIT 200"));
        return products.stream().map(Product::getId).toList();
    }
}
