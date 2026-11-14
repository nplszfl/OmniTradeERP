package com.crossborder.pricing.controller;

import com.crossborder.pricing.entity.CompetitorProduct;
import com.crossborder.pricing.service.CompetitorScrapeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 竞品数据抓取控制器
 */
@RestController
@RequestMapping("/api/v1/competitor")
@RequiredArgsConstructor
@Tag(name = "竞品数据", description = "竞品数据抓取相关接口")
public class CompetitorController {

    private final CompetitorScrapeService competitorScrapeService;

    @Operation(summary = "抓取竞品数据")
    @PostMapping("/scrape/{productId}")
    public String scrapeCompetitorProducts(@PathVariable Long productId) {
        competitorScrapeService.scrapeCompetitorProducts(productId);
        // 📌 这里返回「成功」是**真的**了 —— 服务会真抓取 + 真落库，
        //    全平台失败会抛 BizException → 不会走到这一行。
        return "竞品数据抓取成功！";
    }

    @Operation(summary = "从指定平台抓取")
    @PostMapping("/scrape/platform")
    public String scrapeFromPlatform(
            @RequestParam String platform,
            @RequestParam String productUrl) {
        competitorScrapeService.scrapeFromPlatform(platform, productUrl);
        return "抓取成功！";
    }

    @Operation(summary = "批量抓取")
    @PostMapping("/scrape/batch")
    public String batchScrape(@RequestBody List<Long> productIds) {
        competitorScrapeService.batchScrape(productIds);
        return "批量抓取成功！";
    }

    @Operation(summary = "获取竞品数据")
    @GetMapping("/list/{productId}")
    public List<CompetitorProduct> getCompetitorProducts(@PathVariable Long productId) {
        return competitorScrapeService.getCompetitorProducts(productId);
    }

    @Operation(summary = "获取竞品价格统计")
    @GetMapping("/stats/{productId}")
    public CompetitorScrapeService.CompetitorPriceStats getCompetitorPriceStats(@PathVariable Long productId) {
        return competitorScrapeService.getCompetitorPriceStats(productId);
    }

    @Operation(summary = "手动添加竞品")
    @PostMapping("/add/{productId}")
    public String addCompetitor(
            @PathVariable Long productId,
            @RequestBody CompetitorProduct competitor) {
        competitorScrapeService.addCompetitor(productId, competitor);
        // 📌 真的落库了（原来只打了条日志就说「添加成功」= 假成功）
        return "竞品添加成功！";
    }
}
