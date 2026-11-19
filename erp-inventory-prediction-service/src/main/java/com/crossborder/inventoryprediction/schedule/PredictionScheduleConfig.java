package com.crossborder.inventoryprediction.schedule;

import com.crossborder.inventoryprediction.service.PredictionPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

/**
 * 预测任务定时配置
 *
 * <p>📌 **2026-11-19 新增**：这个模块原先**没有任何定时任务**，
 * 也没人调用落库服务 → {@code sales_prediction} / {@code replenishment_suggestion}
 * 两张表<b>永远是空的</b> → 「库存预测 / 补货建议」这项 AI 能力<b>实际上从未产生过数据</b>，
 * 而 API 看上去完全正常（规则 126：自称有 AI 能力但没有真实产出 = 谎报事实）。
 *
 * <p>📌 **每 6 小时跑一轮**（凌晨 03:20 起步，避开整点高峰）：
 * 预测是日粒度的，没必要每小时重算；日粒度 + 唯一键
 * {@code uk_product_date} → 重跑是<b>更新</b>，不会堆重复行。</p>
 */
@Configuration
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class PredictionScheduleConfig {

    /** 预测天数（与表里日粒度预测一致） */
    private static final int PREDICTION_DAYS = 30;

    private final PredictionPersistenceService predictionPersistenceService;

    /**
     * 库存预测 + 补货建议定时任务。
     *
     * <p>每 6 小时执行一次（cron: 分=20，时=3/9/15/21 时）。
     */
    @Scheduled(cron = "0 20 3/6 * * ?")
    public void predictionSchedule() {
        log.info("🕐 定时任务：开始生成库存预测与补货建议");
        try {
            Map<String, Object> summary = predictionPersistenceService.runPredictionRound(PREDICTION_DAYS);
            log.info("✅ 库存预测完成 - {}", summary);
        } catch (Exception e) {
            // 📌 失败**如实记录**（任务表的 status=FAILED + error_message），不静默吞掉
            log.error("❌ 库存预测任务失败", e);
        }
    }
}
