package com.crossborder.inventoryprediction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 库存预测服务 - v1.5.0 核心AI模块
 *
 * 主要功能：
 * 1. 销售预测 - 基于历史数据预测未来销量
 * 2. 库存预警 - 提前预警库存不足
 * 3. 补货建议 - 智能补货建议
 * 4. 安全库存计算 - 动态计算安全库存水平
 * 5. 库存周转率优化 - 优化库存结构
 *
 * 技术实现：
 * - 时间序列分析（Prophet、ARIMA）
 * - 机器学习模型（scikit-learn）
 * - 异常检测
 * - 趋势分析
 *
 * @author 火球鼠
 * @since 2026-03-17
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class InventoryPredictionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryPredictionServiceApplication.class, args);
    }
}
