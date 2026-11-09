package com.crossborder.analytics.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 📌 **在 H2 上真的把 SQL 执行一遍** —— 这个模块的 SQL 从来没被测过。
 *
 * <p><b>为什么必须有这个测试</b>：本模块以前<b>所有单测都把 Repository mock 掉了</b>
 * → SQL 从来没执行过 → MySQL 专有方言（{@code DATE_FORMAT} / {@code INTERVAL 1 DAY} /
 * {@code DATE_SUB(... WEEKDAY ...)}）能一直藏着，
 * 而 <b>H2（即使 MODE=MySQL）不认</b> → 生产 MySQL 上它是好的 → 语法完全合法 →
 * <b>整条 SQL 抛异常 → 被 catch 吞掉 → 看板「今日/昨日/本月销售额」「按周营收趋势」恒为 0</b>，
 * 页面上完全看不出来（只有一条 WARN 日志）。
 *
 * <p>🔴 四道闸门都没发现的原因：
 * <ol>
 *   <li>单测 mock 掉了 Repository → SQL 根本没执行；</li>
 *   <li>CI 没有 MySQL service → 也没有真库可跑；</li>
 *   <li>生产的 MySQL 上它是好的 → 语法在 MySQL 8 完全合法；</li>
 *   <li>没有闸门查「这条 SQL 在别的库上能不能跑」。</li>
 * </ol>
 *
 * <p>→ 本测试把第 ① 条洞**堵上**：SQL 语法错 = <b>单测直接红</b>。
 *
 * <p>⚠️ <b>反向验证</b>（方法 {@code 旧方言在H2上必须抛异常}）：
 * 这个 H2 环境**必须真的能抓到原缺陷**，否则上面的断言可能只是「什么都没跑」。
 * → 所以显式断言旧写法<b>会抛异常</b>。如果哪天 H2 支持了这些方言，
 * 这个测试会红 → 提醒你把「替代写法」也一起简化（而不是让缺陷复活）。
 */
class AnalyticsRepositoryH2Test {

    private JdbcTemplate jdbcTemplate;
    private AnalyticsRepository repository;

    /**
     * 📌 **今天的时间锚点：固定在正午 12:00** —— 绝对不能用 {@code LocalDateTime.now()}。
     *
     * <p>🔴 这里曾经埋了一颗<b>时间炸弹</b>：种子数据原本用 {@code now} 和
     * {@code now.minusHours(1)} 表示「今天的两单」。
     * → 在 <b>00:00~00:59</b> 之间跑，{@code now.minusHours(1)} 会落到<b>昨天</b> →
     * 「今日销售额」少 200、「按小时聚合」少 1 单 → <b>单测直接红</b>。
     * <p>而它是 <b>23:54</b> 提交的，CI 也是那个点跑的 → <b>全绿</b> →
     * 缺陷能静静躺到第二天凌晨才爆（本次就是 00:16 复现的）。
     *
     * <p>→ 锚点改成 {@code today.atTime(12, 0)}：无论几点跑，11:00 / 12:00 都稳稳落在今天。
     * <p>（SQL 侧按 <b>日期区间</b> {@code [dayStart, nextDayStart)} 过滤，
     * 不是「{@code create_time <= now}」→ 所以中午这个锚点即便「还没到」也不会被漏掉。）
     */
    private LocalDateTime todayAnchor;

    @BeforeEach
    void setUp() {
        // 📌 每个测试用**独立的库名** → 互不干扰，且不需要 DB_CLOSE_DELAY
        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriverClass(org.h2.Driver.class);
        // 📌 `DB_CLOSE_DELAY=-1` **不能少**：SimpleDriverDataSource 每次 getConnection()
        //    都是**新连接**，不加它 → 连接一关内存库就被销毁 →
        //    「CREATE SCHEMA」和「CREATE TABLE」落在**两个不同的库**上 → Schema not found。
        ds.setUrl("jdbc:h2:mem:analytics_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        this.jdbcTemplate = new JdbcTemplate(ds);
        // 📌 锚点必须在建表/播种**之前**算好 —— 种子数据里所有「今天」的时间都从它派生
        this.todayAnchor = LocalDate.now().atTime(12, 0);
        createSchema();
        seedData();
        this.repository = new AnalyticsRepository(jdbcTemplate);
    }

    // ---------------------------------------------------------------- schema / 数据

    private void createSchema() {
        // 📌 H2 原生支持 **schema** → 跨库引用 `erp_order.t_order` 可以直接建
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS erp_order");
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS erp_product");
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS erp_inventory");

        jdbcTemplate.execute("CREATE TABLE erp_order.t_order ("
                + "id BIGINT PRIMARY KEY,"
                + "internal_order_no VARCHAR(64),"
                + "platform VARCHAR(32),"
                + "status VARCHAR(32),"
                + "order_amount DECIMAL(18,2),"
                + "currency_code VARCHAR(8),"
                + "recipient_country VARCHAR(64),"
                + "create_time TIMESTAMP NOT NULL,"
                + "deleted INT NOT NULL DEFAULT 0)");

        jdbcTemplate.execute("CREATE TABLE erp_order.t_order_item ("
                + "id BIGINT PRIMARY KEY,"
                + "order_id BIGINT NOT NULL,"
                + "internal_sku VARCHAR(100),"
                + "product_name VARCHAR(200),"
                + "quantity INT NOT NULL DEFAULT 0,"
                + "total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,"
                + "deleted INT NOT NULL DEFAULT 0)");

        jdbcTemplate.execute("CREATE TABLE erp_product.t_product_sku ("
                + "id BIGINT PRIMARY KEY,"
                + "product_id BIGINT,"
                + "sku_code VARCHAR(100),"
                + "cost_price DECIMAL(18,2) NOT NULL DEFAULT 0,"
                + "deleted INT NOT NULL DEFAULT 0)");

        jdbcTemplate.execute("CREATE TABLE erp_product.t_product ("
                + "id BIGINT PRIMARY KEY,"
                + "internal_sku VARCHAR(100),"
                + "product_name VARCHAR(200),"
                + "status INT NOT NULL DEFAULT 1,"
                + "deleted INT NOT NULL DEFAULT 0)");

        jdbcTemplate.execute("CREATE TABLE erp_inventory.erp_inventory ("
                + "id BIGINT PRIMARY KEY,"
                + "sku_code VARCHAR(100),"
                + "available_quantity INT NOT NULL DEFAULT 0,"
                + "safety_stock INT NOT NULL DEFAULT 0,"
                + "deleted INT NOT NULL DEFAULT 0)");
    }

    private void seedData() {
        LocalDate today = LocalDate.now();
        // 📌 用锚点，不用 now() —— 理由见 todayAnchor 的注释（0 点后会跨到昨天）
        LocalDateTime now = todayAnchor;

        // 今天：2 单 300.00
        insertOrder(1L, "SO-T-1", "AMAZON", "COMPLETED", "100.00", "US", now);
        insertOrder(2L, "SO-T-2", "AMAZON", "PENDING", "200.00", "US", now.minusHours(1));
        // 昨天：1 单 50.00
        insertOrder(3L, "SO-Y-1", "EBAY", "COMPLETED", "50.00", "DE",
                now.minusDays(1));
        // 上个月（本月之外）：1 单 999.00 —— 用来验证「本月」不被它污染
        // 📌 **必须是「1 号减 2 天」，不能是「1 号减 1 天」**：
        //    「1 号减 1 天」= 上个月最后一天 = **今天恰好是本月 1 号时的「昨天」**
        //    → 两单落在同一天 → 「昨日销售额」变成 50 + 999 = 1049 → 断言红。
        //    → 「减 2 天」永远在上个月，且**永远不是昨天**。
        insertOrder(4L, "SO-M-1", "TIKTOK", "COMPLETED", "999.00", "FR",
                today.withDayOfMonth(1).minusDays(2).atStartOfDay());

        insertItem(11L, 1L, "SKU-001", "无线耳机", 1, "100.00");
        insertItem(12L, 2L, "SKU-002", "蓝牙音箱", 2, "200.00");
        insertItem(13L, 3L, "SKU-003", "手机壳", 1, "50.00");
        insertItem(14L, 4L, "SKU-004", "数据线", 1, "999.00");

        jdbcTemplate.update("INSERT INTO erp_product.t_product_sku (id, product_id, sku_code, cost_price, deleted)"
                + " VALUES (101, 201, 'SKU-001', 40.00, 0)");
        jdbcTemplate.update("INSERT INTO erp_product.t_product_sku (id, product_id, sku_code, cost_price, deleted)"
                + " VALUES (102, 202, 'SKU-002', 80.00, 0)");
        jdbcTemplate.update("INSERT INTO erp_product.t_product_sku (id, product_id, sku_code, cost_price, deleted)"
                + " VALUES (103, 203, 'SKU-003', 10.00, 0)");
        jdbcTemplate.update("INSERT INTO erp_product.t_product_sku (id, product_id, sku_code, cost_price, deleted)"
                + " VALUES (104, 204, 'SKU-004', 5.00, 0)");

        jdbcTemplate.update("INSERT INTO erp_product.t_product (id, internal_sku, product_name, status, deleted)"
                + " VALUES (201, 'SKU-001', '无线耳机', 1, 0)");
        jdbcTemplate.update("INSERT INTO erp_product.t_product (id, internal_sku, product_name, status, deleted)"
                + " VALUES (202, 'SKU-002', '蓝牙音箱', 1, 0)");
        jdbcTemplate.update("INSERT INTO erp_product.t_product (id, internal_sku, product_name, status, deleted)"
                + " VALUES (203, 'SKU-003', '手机壳', 1, 0)");
        jdbcTemplate.update("INSERT INTO erp_product.t_product (id, internal_sku, product_name, status, deleted)"
                + " VALUES (204, 'SKU-004', '数据线', 1, 0)");
    }

    private void insertOrder(long id, String no, String platform, String status,
                             String amount, String country, LocalDateTime time) {
        jdbcTemplate.update("INSERT INTO erp_order.t_order (id, internal_order_no, platform, status,"
                        + " order_amount, currency_code, recipient_country, create_time, deleted)"
                        + " VALUES (?, ?, ?, ?, ?, 'USD', ?, ?, 0)",
                id, no, platform, status, new BigDecimal(amount), country, time);
    }

    private void insertItem(long id, long orderId, String sku, String name, int qty, String amount) {
        jdbcTemplate.update("INSERT INTO erp_order.t_order_item (id, order_id, internal_sku,"
                        + " product_name, quantity, total_amount, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
                id, orderId, sku, name, qty, new BigDecimal(amount));
    }

    // ---------------------------------------------------------------- 测试

    @Test
    @DisplayName("销售额汇总：今日/昨日/本月/累计 都要有真实数字（原来恒为 0）")
    void salesSummaryMustReturnRealNumbers() {
        AnalyticsRepository.SalesSummary s = repository.getSalesSummary();

        assertEquals(0, s.todaySales().compareTo(new BigDecimal("300.00")),
                "今日销售额应为 300.00，实际 " + s.todaySales());
        assertEquals(0, s.yesterdaySales().compareTo(new BigDecimal("50.00")),
                "昨日销售额应为 50.00，实际 " + s.yesterdaySales());
        // 📌 「本月」的期望值必须**跟着日历算**：昨天是否落在本月，取决于「今天是不是本月 1 号」
        //    → 写死 350.00 就等于在每月 1 号那天埋下一颗新的时间炸弹
        BigDecimal expectedMonth = expectedMonthSales();
        assertEquals(0, s.monthSales().compareTo(expectedMonth),
                "本月销售额应为 " + expectedMonth + "（今天 300.00 + 昨天是否落在本月），实际 " + s.monthSales());
        assertEquals(0, s.totalSales().compareTo(new BigDecimal("1349.00")),
                "累计销售额应为 1349.00，实际 " + s.totalSales());
        assertEquals(4L, s.totalOrders(), "总订单数应为 4");
        assertEquals(1L, s.pendingOrders(), "待发货订单应为 1");
    }

    @Test
    @DisplayName("按小时聚合：今天的单必须能被分桶（原来恒为空）")
    void hourlyAggregatesMustNotBeEmpty() {
        List<AnalyticsRepository.HourlyAggregate> rows = repository.getHourlyAggregates();

        assertFalse(rows.isEmpty(), "按小时聚合不应为空（H2 不认 CURDATE() 时会空）");
        long count = rows.stream().mapToLong(AnalyticsRepository.HourlyAggregate::orderCount).sum();
        assertEquals(2L, count, "今天应有 2 单");
    }

    /**
     * 「本月」的期望值 —— <b>必须跟着日历算</b>。
     *
     * <p>SQL 侧「本月」的口径是 {@code create_time >= 当月 1 号 00:00}（无上界），
     * 所以「昨天那一单」到底算不算本月，取决于<b>今天是不是本月 1 号</b>。
     * → 把期望值写死成 {@code 350.00}，就等于在<b>每月 1 号</b>那天埋一颗新的时间炸弹
     * （和 {@code now.minusHours(1)} 同一类病：断言依赖「跑测试的那一刻」）。
     */
    private static BigDecimal expectedMonthSales() {
        LocalDate today = LocalDate.now();
        BigDecimal expected = new BigDecimal("300.00");   // 今天的两单
        if (isSameMonth(today.minusDays(1), today)) {
            expected = expected.add(new BigDecimal("50.00"));   // 昨天也在本月
        }
        return expected;
    }

    private static boolean isSameMonth(LocalDate a, LocalDate b) {
        return a.getYear() == b.getYear() && a.getMonthValue() == b.getMonthValue();
    }

    @Test
    @DisplayName("回归：种子里「今天的两单」必须真的落在今天（防止再写回 now.minusHours(1)）")
    void seededTodayOrdersMustReallyBeToday() {
        // 📌 直接查库核对种子落在哪一天 → 不管将来种子怎么改，这条断言都抓得住
        Integer todayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM erp_order.t_order WHERE DATE(create_time) = DATE(?) AND deleted = 0",
                Integer.class, LocalDate.now());
        Integer yesterdayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM erp_order.t_order WHERE DATE(create_time) = DATE(?) AND deleted = 0",
                Integer.class, LocalDate.now().minusDays(1));
        Integer prevMonthCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM erp_order.t_order WHERE DATE(create_time) < ? AND deleted = 0",
                Integer.class, LocalDate.now().withDayOfMonth(1));

        assertEquals(2, todayCount,
                "今天必须有 2 单 —— 若种子改回 now.minusHours(1)，00:00~00:59 跑就会变成 1 单");
        assertEquals(1, yesterdayCount, "昨天必须正好 1 单");
        // 📌 「1 号之前」的单数**也会随「今天是不是本月 1 号」变**：
        //    今天是 1 号时，「昨天那单」也落在上月 → 2 单；否则只有「上月那单」→ 1 单。
        int expectedBeforeMonth = 1 + (LocalDate.now().getDayOfMonth() == 1 ? 1 : 0);
        assertEquals(expectedBeforeMonth, prevMonthCount,
                "本月 1 号之前应有 " + expectedBeforeMonth + " 单（验证「本月」不会被它污染）");
    }

    @Test
    @DisplayName("按周聚合：折周后仍有数据（原来恒为空）")
    void weeklyAggregatesMustNotBeEmpty() {
        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().plusDays(1);
        List<AnalyticsRepository.WeeklyAggregate> rows = repository.getWeeklyAggregates(start, end);

        assertFalse(rows.isEmpty(), "按周聚合不应为空（H2 不认 DATE_SUB(... WEEKDAY ...) 时会空）");
        BigDecimal revenue = rows.stream()
                .map(AnalyticsRepository.WeeklyAggregate::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(revenue.compareTo(BigDecimal.ZERO) > 0, "周聚合营收应大于 0，实际 " + revenue);
    }

    @Test
    @DisplayName("平台聚合 + Top 商品：跨 schema JOIN 也要真跑得通")
    void crossSchemaJoinsMustWork() {
        var platforms = repository.getPlatformAggregates(LocalDate.now().minusDays(30),
                LocalDate.now().plusDays(1));
        assertFalse(platforms.isEmpty(), "平台聚合不应为空");
        assertTrue(platforms.stream().anyMatch(p -> "AMAZON".equals(p.platform())),
                "应聚合出 AMAZON 平台");

        var top = repository.getTopProducts(10);
        assertFalse(top.isEmpty(), "Top 商品不应为空");
        // ⚠️ `getTopProducts(int)` 是**全时段**口径（SQL 里没有时间过滤）
        // → 营收最高的是上月那单 SKU-004（999.00），不是今天的 SKU-002。
        assertEquals("SKU-004", top.get(0).sku(), "全时段营收第一应是 SKU-004（999.00）");
        assertEquals(0, top.get(0).revenue().compareTo(new BigDecimal("999.00")),
                "SKU-004 营收应为 999.00，实际 " + top.get(0).revenue());
        assertTrue(top.stream().anyMatch(p -> "SKU-001".equals(p.sku())), "应包含 SKU-001");
        assertEquals("无线耳机", top.stream().filter(p -> "SKU-001".equals(p.sku()))
                .findFirst().orElseThrow().productName(), "商品名应从 erp_product 关联出来");
    }

    @Test
    @DisplayName("⚠️ 反向验证：旧方言在 H2 上必须抛异常（证明这个环境真能抓到原缺陷）")
    void legacyDialectMustFailOnH2() {
        // 原缺陷 1：DATE_FORMAT
        assertThrows(Exception.class, () -> jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(CURDATE(), '%Y-%m-01')", String.class));
        // 原缺陷 2：INTERVAL 算术式
        assertThrows(Exception.class, () -> jdbcTemplate.queryForObject(
                "SELECT CURDATE() - INTERVAL 1 DAY", String.class));
        // 原缺陷 3：DATE_SUB + WEEKDAY
        assertThrows(Exception.class, () -> jdbcTemplate.queryForObject(
                "SELECT DATE_SUB(DATE(CURRENT_TIMESTAMP), INTERVAL WEEKDAY(CURRENT_TIMESTAMP) DAY)",
                String.class));
    }
}
