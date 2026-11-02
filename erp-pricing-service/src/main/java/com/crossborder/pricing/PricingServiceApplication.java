package com.crossborder.pricing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能定价服务 - v1.5.0 核心功能
 *
 * 主要功能：
 * 1. 竞品数据抓取 - 实时抓取Amazon、eBay、Shopee等平台的竞品价格
 * 2. 智能定价算法 - 基于竞品分析、成本、利润率、季节性等多因素定价
 * 3. 动态定价策略 - 自动调整价格，适应市场变化
 * 4. 价格历史追踪 - 记录价格变更历史，支持趋势分析
 *
 * @author 火球鼠
 * @since 2026-03-17
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
// ⚠️ 必须加：pricing-service 的 Mapper 在 com.crossborder.pricing.mapper 包下，
//    而 @SpringBootApplication 扫的是 com.crossborder.pricing —— 其实能扫到，
//    但显式声明更清楚，也避免以后包结构调整时静默失效。
@MapperScan("com.crossborder.pricing.mapper")
public class PricingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingServiceApplication.class, args);
    }
}
