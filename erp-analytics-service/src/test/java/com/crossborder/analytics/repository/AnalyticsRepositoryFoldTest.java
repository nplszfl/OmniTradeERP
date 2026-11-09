package com.crossborder.analytics.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnalyticsRepository#foldDailyToWeekly} 单元测试
 *
 * <p>📌 为什么这段逻辑值得单测：它以前是一段 MySQL 方言 SQL
 * {@code DATE_SUB(DATE(x), INTERVAL WEEKDAY(x) DAY)} —— H2 不认，
 * 整条 SQL 抛异常后被 catch 吞掉 → 营收趋势图恒为空，
 * 而**单测根本碰不到它**（Repository 被 mock 掉了）。
 * 折成周现在是纯函数，可以直接验。</p>
 */
@DisplayName("按天聚合折成按周聚合")
class AnalyticsRepositoryFoldTest {

    private static AnalyticsRepository.DailyRevenueCost day(String iso, String rev, String cost) {
        return new AnalyticsRepository.DailyRevenueCost(
                LocalDate.parse(iso), new BigDecimal(rev), new BigDecimal(cost));
    }

    /** 独立复刻 MySQL 的 {@code DATE_SUB(DATE(d), INTERVAL WEEKDAY(d) DAY)} 语义（周一=0…周日=6）。 */
    private static LocalDate mysqlWeekStart(LocalDate d) {
        int weekday = d.getDayOfWeek().getValue() - 1; // 周一=0 … 周日=6
        return d.minusDays(weekday);
    }

    @Test
    @DisplayName("单日 → 一周，营收/成本原样带过去")
    void singleDay() {
        List<AnalyticsRepository.WeeklyAggregate> out =
                AnalyticsRepository.foldDailyToWeekly(List.of(day("2026-11-04", "120.50", "40.25")));

        assertEquals(1, out.size());
        assertEquals(LocalDate.parse("2026-11-02"), out.get(0).weekStart()); // 2026-11-04 是周二 → 上一周一
        assertEquals(new BigDecimal("120.50"), out.get(0).revenue());
        assertEquals(new BigDecimal("40.25"), out.get(0).cost());
    }

    @Test
    @DisplayName("周一自身归属该周（previousOrSame 语义）")
    void mondayStaysMonday() {
        List<AnalyticsRepository.WeeklyAggregate> out =
                AnalyticsRepository.foldDailyToWeekly(List.of(day("2026-11-02", "10", "5")));

        assertEquals(1, out.size());
        assertEquals(LocalDate.parse("2026-11-02"), out.get(0).weekStart());
    }

    @Test
    @DisplayName("周日归属上一个周一（不是下一个）")
    void sundayBelongsToPreviousMonday() {
        List<AnalyticsRepository.WeeklyAggregate> out =
                AnalyticsRepository.foldDailyToWeekly(List.of(day("2026-11-08", "10", "5")));

        assertEquals(1, out.size());
        assertEquals(LocalDate.parse("2026-11-02"), out.get(0).weekStart());
    }

    @Test
    @DisplayName("跨周 → 拆成两周并累加")
    void splitsAcrossWeeks() {
        List<AnalyticsRepository.WeeklyAggregate> out = AnalyticsRepository.foldDailyToWeekly(List.of(
                day("2026-11-02", "100.00", "40.00"),   // 周一
                day("2026-11-08", "50.00", "20.00"),    // 周日 → 上一周
                day("2026-11-09", "25.00", "10.00")));  // 周一 → 新的一周

        assertEquals(2, out.size());
        assertEquals(LocalDate.parse("2026-11-02"), out.get(0).weekStart());
        assertEquals(new BigDecimal("150.00"), out.get(0).revenue());
        assertEquals(new BigDecimal("60.00"), out.get(0).cost());
        assertEquals(LocalDate.parse("2026-11-09"), out.get(1).weekStart());
        assertEquals(new BigDecimal("25.00"), out.get(1).revenue());
    }

    @Test
    @DisplayName("金额先累加、最后才舍入到两位（与原 SQL 一致）")
    void roundsOnlyAtTheEnd() {
        List<AnalyticsRepository.WeeklyAggregate> out = AnalyticsRepository.foldDailyToWeekly(List.of(
                day("2026-11-02", "0.005", "0.004"),
                day("2026-11-03", "0.005", "0.004")));

        // 先累加：0.010 / 0.008 → 再 HALF_UP 到两位
        assertEquals(new BigDecimal("0.01"), out.get(0).revenue());
        assertEquals(new BigDecimal("0.01"), out.get(0).cost());
    }

    @Test
    @DisplayName("null 金额当 0，不炸")
    void nullsBecomeZero() {
        List<AnalyticsRepository.WeeklyAggregate> out = AnalyticsRepository.foldDailyToWeekly(
                List.of(new AnalyticsRepository.DailyRevenueCost(LocalDate.parse("2026-11-02"), null, null)));

        assertEquals(1, out.size());
        assertEquals(new BigDecimal("0.00"), out.get(0).revenue());
        assertEquals(new BigDecimal("0.00"), out.get(0).cost());
    }

    @Test
    @DisplayName("空列表 → 空结果")
    void emptyInput() {
        assertTrue(AnalyticsRepository.foldDailyToWeekly(List.of()).isEmpty());
    }

    @Test
    @DisplayName("与 MySQL WEEKDAY 公式逐日对照（交叉验证，不是自证）")
    void matchesMysqlWeekdaySemantics() {
        LocalDate start = LocalDate.parse("2026-10-26");
        for (int i = 0; i < 42; i++) {   // 连续 6 周，覆盖每个星期几
            LocalDate d = start.plusDays(i);
            List<AnalyticsRepository.WeeklyAggregate> out =
                    AnalyticsRepository.foldDailyToWeekly(List.of(day(d.toString(), "1", "1")));
            assertEquals(mysqlWeekStart(d), out.get(0).weekStart(),
                    "日期 " + d + "（周" + d.getDayOfWeek().getValue() + "）折周结果与 MySQL 语义不一致");
            assertEquals(DayOfWeek.MONDAY, out.get(0).weekStart().getDayOfWeek());
        }
    }
}
