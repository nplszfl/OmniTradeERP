package com.crossborder.inventoryprediction.service;

import java.util.List;
import java.util.Map;

/**
 * 预测结果落库服务（写路径）
 *
 * <p>📌 **2026-11-19 新增**：这个模块原先**只有读路径、没有写路径** ——
 * {@code sales_prediction} / {@code replenishment_suggestion} / {@code prediction_task}
 * 三张表都有完整 DDL，却**没有任何实体、没有任何写入口** →
 * <b>永远是空表</b> → 读这些表的看板/报表永远是空白，
 * 而 API（{@code /predict}、{@code /replenishment/{id}}）看上去完全正常
 * → 「功能看起来在，数据其实从没产生过」（规则 126 的同款病灶）。
 *
 * <p>📌 **本服务只做三件事**：
 * <ol>
 *   <li>把 {@link com.crossborder.inventoryprediction.service.InventoryPredictionService}
 *       算出来的<b>日粒度预测</b>写入 {@code sales_prediction}（同产品同日期唯一 → 重跑是<b>更新</b>）；</li>
 *   <li>把补货建议写入 {@code replenishment_suggestion}（同产品的待审批建议<b>更新</b>而不是刷一片）；</li>
 *   <li>在 {@code prediction_task} 写一行<b>任务审计</b>（状态 / 耗时 / 结果摘要 / 错误信息）。</li>
 * </ol>
 *
 * <p>📌 **不编数字**：没有销售历史 → 预测为空 → <b>如实记 0 条</b>，
 * 任务状态仍是 SUCCESS 但摘要写明「无销售历史」——
 * 让「没数据」和「跑失败」在表里可区分（规则 89/101）。</p>
 */
public interface PredictionPersistenceService {

    /**
     * 跑一轮「销量预测 + 补货建议」并落库。
     *
     * @param predictionDays 预测天数（默认 30）
     * @return 结果摘要（产品数 / 预测行数 / 建议行数 / 跳过原因）
     */
    Map<String, Object> runPredictionRound(int predictionDays);

    /**
     * 当前会被纳入预测的产品ID（<b>真查库存表</b>，不是写死的 ID 列表）。
     *
     * <p>📌 写死 {@code List.of(1L..5L)} 的问题是：库存表里没这些产品时，
     * 任务每轮都在「抓空气」，而日志会说「没有需要监控的产品」——
     * <b>看起来一切正常，实际什么都没做</b>（规则 55：闸门判据必须匹配代码实际怎么写）。</p>
     */
    List<Long> getMonitoredProductIds();
}
