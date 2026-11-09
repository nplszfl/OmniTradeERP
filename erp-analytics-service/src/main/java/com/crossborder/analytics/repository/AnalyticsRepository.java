package com.crossborder.analytics.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析数据仓储（真实查库）
 *
 * <p>⚠️ 之前这个服务没有任何持久层，所有数字都是 {@code Math.random()} 造出来的，
 * 而且 datasource 指向一个根本不存在的 {@code omnitrade_erp} 库 ——
 * 单测全绿、接口能返回，但页面上的数全是编的。</p>
 *
 * <p>数据来源（同一个 MySQL 实例，跨库 JOIN）：</p>
 * <ul>
 *   <li>{@code erp_order.t_order} — 订单主表（金额、状态、平台、时间）</li>
 *   <li>{@code erp_order.t_order_item} — 订单明细（SKU、销量）</li>
 *   <li>{@code erp_product.t_product} — 商品主表（名称）</li>
 *   <li>{@code erp_inventory.erp_inventory} — 库存（可用量、安全库存）</li>
 * </ul>
 *
 * <p>跨库 JOIN 要求所有 schema 在同一 MySQL 实例上，compose 里是同一个 mysql 容器，
 * 所以可行。如果某张表不存在，查询会抛异常 —— 这里统一 catch 住返回空，
 * 保证单个数据源缺失不会让整个看板 500。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------- 记录模型

    /** 某一天的订单数与营收 */
    public record DailyAggregate(LocalDate date, long orderCount, BigDecimal revenue) {}

    /** 某个平台的订单数与营收 */
    public record PlatformAggregate(String platform, long orderCount, BigDecimal revenue,
                                    BigDecimal cost) {}

    /** 某个商品的销量与营收 */
    public record ProductAggregate(String sku, String productName, long sold, BigDecimal revenue,
                                   BigDecimal cost) {}

    /** 某个区间的汇总 */
    public record PeriodAggregate(BigDecimal revenue, BigDecimal cost, long orderCount,
                                   long unitsSold) {}

    /** 竞品信息 */
    public record CompetitorRow(String competitorName, String platform, BigDecimal price,
                                 BigDecimal rating, long reviewCount, long salesVolume) {}

    /** 某个 SKU 的当前售价 */
    public record ProductPrice(String sku, BigDecimal salePrice) {}

    /** 某一周（周一）的营收与成本 */
    public record WeeklyAggregate(LocalDate weekStart, BigDecimal revenue, BigDecimal cost) {}

    /** 最近订单 */
    public record RecentOrder(String orderId, String platform, String status,
                               BigDecimal amount, LocalDateTime orderTime) {}

    /** 某个国家/地区的客户数与营收 */
    public record RegionAggregate(String region, long customers, BigDecimal revenue) {}

    /** 库存预警 */
    public record StockAlert(String sku, String productName, int currentStock,
                             int safetyStock, String alertLevel) {}

    /** 某个小时的订单数与营收 */
    public record HourlyAggregate(int hour, long orderCount, BigDecimal revenue) {}

    /** 某个订单状态的订单数 */
    public record OrderStatusCount(String status, long count) {}

    /** 销售额汇总 */
    public record SalesSummary(BigDecimal todaySales, BigDecimal yesterdaySales,
                               BigDecimal monthSales, BigDecimal totalSales,
                               long totalOrders, long pendingOrders,
                               long shippedOrders, long completedOrders) {}

    // ---------------------------------------------------------------- 销售额汇总

    /**
     * 各时间段的销售额与订单数
     *
     * <p>全部来自 {@code erp_order.t_order}。</p>
     */
    public SalesSummary getSalesSummary() {
        try {
            // ⚠️ 时间边界一律用**参数**传，不要 CURDATE() / DATE_FORMAT / INTERVAL：
            //    那是 MySQL 方言，H2（即使 MODE=MySQL）不认 → 整条 SQL 抛异常 →
            //    被下面的 catch 吞掉 → 看板「今日/昨日/本月销售额」**恒为 0**，
            //    而且页面上只有一条 WARN 日志，看起来一切正常。
            //    （同一个坑 InventoryNameResolver 已经修过一次，注释还在那儿。）
            LocalDate day = LocalDate.now();
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime nextDayStart = day.plusDays(1).atStartOfDay();
            BigDecimal today = sumOrders("create_time >= ? AND create_time < ?", dayStart, nextDayStart);
            BigDecimal yesterday = sumOrders("create_time >= ? AND create_time < ?",
                    day.minusDays(1).atStartOfDay(), dayStart);
            BigDecimal month = sumOrders("create_time >= ?", day.withDayOfMonth(1).atStartOfDay());
            BigDecimal total = sumOrders("1 = 1");

            long totalOrders = countOrders("1 = 1");
            long pending = countOrders("status IN ('PENDING','PROCESSING','CREATED','PAID')");
            long shipped = countOrders("status = 'SHIPPED'");
            long completed = countOrders("status IN ('COMPLETED','DELIVERED','SUCCESS')");

            return new SalesSummary(nz(today), nz(yesterday), nz(month), nz(total),
                    totalOrders, pending, shipped, completed);
        } catch (Exception e) {
            log.warn("查询销售汇总失败，返回空值: {}", e.getMessage());
            return new SalesSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0, 0, 0, 0);
        }
    }

    private BigDecimal sumOrders(String where, Object... args) {
        BigDecimal v = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(order_amount), 0) FROM erp_order.t_order WHERE "
                        + where + " AND deleted = 0", BigDecimal.class, args);
        return nz(v);
    }

    private long countOrders(String where, Object... args) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM erp_order.t_order WHERE " + where + " AND deleted = 0",
                Long.class, args);
        return v == null ? 0 : v;
    }

    // ---------------------------------------------------------------- 按小时聚合

    /**
     * 今日每小时（0-23）的订单数与营收
     */
    public List<HourlyAggregate> getHourlyAggregates() {
        Map<Integer, HourlyAggregate> bucket = new java.util.TreeMap<>();
        try {
            // 📌 今天的时间窗用参数传（MySQL 方言 CURDATE() 在 H2 不认，详见 getSalesSummary）
            LocalDate day = LocalDate.now();
            List<HourlyAggregate> rows = jdbcTemplate.query(
                    "SELECT HOUR(create_time) AS h, COUNT(*) AS cnt, "
                            + "COALESCE(SUM(order_amount), 0) AS rev "
                            + "FROM erp_order.t_order "
                            + "WHERE create_time >= ? AND create_time < ? AND deleted = 0 "
                            + "GROUP BY HOUR(create_time)",
                    (rs, i) -> new HourlyAggregate(
                            rs.getInt("h"),
                            rs.getLong("cnt"),
                            nz(rs.getBigDecimal("rev"))),
                    day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            for (HourlyAggregate r : rows) {
                bucket.put(r.hour(), r);
            }
        } catch (Exception e) {
            log.warn("查询小时聚合失败: {}", e.getMessage());
        }
        List<HourlyAggregate> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            out.add(bucket.getOrDefault(h, new HourlyAggregate(h, 0L, BigDecimal.ZERO)));
        }
        return out;
    }

    // ---------------------------------------------------------------- 按天聚合

    /**
     * 近 {@code days} 天的每日订单数与营收
     *
     * <p>没有订单的日期会补 0（前端画趋势图需要连续日期）。</p>
     */
    public List<DailyAggregate> getDailyAggregates(int days) {
        int span = days <= 0 ? 30 : Math.min(days, 365);
        LocalDate from = LocalDate.now().minusDays(span - 1L);

        Map<LocalDate, DailyAggregate> bucket = new LinkedHashMap<>();
        try {
            List<DailyAggregate> rows = jdbcTemplate.query(
                    "SELECT DATE(create_time) AS d, COUNT(*) AS cnt, "
                            + "COALESCE(SUM(order_amount), 0) AS rev "
                            + "FROM erp_order.t_order "
                            + "WHERE DATE(create_time) >= ? AND deleted = 0 "
                            + "GROUP BY DATE(create_time)",
                    (rs, i) -> new DailyAggregate(
                            rs.getDate("d").toLocalDate(),
                            rs.getLong("cnt"),
                            nz(rs.getBigDecimal("rev"))),
                    from);

            for (DailyAggregate r : rows) {
                bucket.put(r.date(), r);
            }
        } catch (Exception e) {
            log.warn("查询日聚合失败: {}", e.getMessage());
        }

        List<DailyAggregate> out = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            LocalDate d = from.plusDays(i);
            DailyAggregate hit = bucket.get(d);
            out.add(hit != null ? hit
                    : new DailyAggregate(d, 0L, BigDecimal.ZERO));
        }
        return out;
    }

    // ---------------------------------------------------------------- 按平台聚合

    /**
     * 各平台的订单数与营收
     */
    public List<PlatformAggregate> getPlatformAggregates() {
        return getPlatformAggregates(null, null);
    }

    /**
     * 各平台的订单数、营收与成本（可选时间区间）
     *
     * <p>成本来自 {@code erp_product.t_product_sku.cost_price} × 销量，
     * 跨库 JOIN。</p>
     */
    public List<PlatformAggregate> getPlatformAggregates(LocalDate start, LocalDate end) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT o.platform AS platform, "
                            + "COUNT(DISTINCT o.id) AS cnt, "
                            + "COALESCE(SUM(oi.total_amount), 0) AS rev, "
                            + "COALESCE(SUM(oi.quantity * s.cost_price), 0) AS cost "
                            + "FROM erp_order.t_order o "
                            + "LEFT JOIN erp_order.t_order_item oi "
                            + "  ON oi.order_id = o.id AND oi.deleted = 0 "
                            + "LEFT JOIN erp_product.t_product_sku s ON s.sku_code = oi.internal_sku AND s.deleted = 0 "
                            + "WHERE o.deleted = 0 ");
            List<Object> args = new ArrayList<>();
            if (start != null) {
                sql.append("AND DATE(o.create_time) >= ? ");
                args.add(start);
            }
            if (end != null) {
                sql.append("AND DATE(o.create_time) <= ? ");
                args.add(end);
            }
            sql.append("GROUP BY o.platform ORDER BY rev DESC");

            return jdbcTemplate.query(sql.toString(),
                    (rs, i) -> new PlatformAggregate(
                            rs.getString("platform"),
                            rs.getLong("cnt"),
                            nz(rs.getBigDecimal("rev")),
                            nz(rs.getBigDecimal("cost"))),
                    args.toArray());
        } catch (Exception e) {
            log.warn("查询平台聚合失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 时间区间的营收 / 成本 / 订单数 / 销量
     */
    public PeriodAggregate getPeriodAggregate(LocalDate start, LocalDate end) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(oi.total_amount), 0) AS rev, "
                            + "COALESCE(SUM(oi.quantity * s.cost_price), 0) AS cost, "
                            + "COUNT(DISTINCT o.id) AS orders, "
                            + "COALESCE(SUM(oi.quantity), 0) AS units "
                            + "FROM erp_order.t_order o "
                            + "LEFT JOIN erp_order.t_order_item oi "
                            + "  ON oi.order_id = o.id AND oi.deleted = 0 "
                            + "LEFT JOIN erp_product.t_product_sku s ON s.sku_code = oi.internal_sku AND s.deleted = 0 "
                            + "WHERE o.deleted = 0 AND DATE(o.create_time) >= ? "
                            + "AND DATE(o.create_time) <= ?",
                    (rs, i) -> new PeriodAggregate(
                            nz(rs.getBigDecimal("rev")),
                            nz(rs.getBigDecimal("cost")),
                            rs.getLong("orders"),
                            rs.getLong("units")),
                    start, end);
        } catch (Exception e) {
            log.warn("查询区间汇总失败: {}", e.getMessage());
            return new PeriodAggregate(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }
    }

    /**
     * 按周聚合的营收与成本（周一起始）
     */
    public List<WeeklyAggregate> getWeeklyAggregates(LocalDate start, LocalDate end) {
        try {
            // 📌 **按天聚合后在 Java 里折成周**，而不是在 SQL 里写
            //    `DATE_SUB(DATE(x), INTERVAL WEEKDAY(x) DAY)`：
            //    那是 MySQL 方言，H2 不认 → 整条 SQL 抛异常 → 被下面的 catch 吞掉 →
            //    营收趋势图恒为空（只剩一条 WARN 日志）。
            //    折周是纯函数（foldDailyToWeekly），能单测；原来那段 SQL 谁也测不了。
            List<DailyRevenueCost> daily = jdbcTemplate.query(
                    "SELECT DATE(o.create_time) AS d, "
                            + "COALESCE(SUM(oi.total_amount), 0) AS rev, "
                            + "COALESCE(SUM(oi.quantity * s.cost_price), 0) AS cost "
                            + "FROM erp_order.t_order o "
                            + "LEFT JOIN erp_order.t_order_item oi "
                            + "  ON oi.order_id = o.id AND oi.deleted = 0 "
                            + "LEFT JOIN erp_product.t_product_sku s ON s.sku_code = oi.internal_sku AND s.deleted = 0 "
                            + "WHERE o.deleted = 0 AND DATE(o.create_time) >= ? "
                            + "AND DATE(o.create_time) <= ? "
                            + "GROUP BY DATE(o.create_time) ORDER BY d",
                    (rs, i) -> new DailyRevenueCost(
                            rs.getDate("d").toLocalDate(),
                            rs.getBigDecimal("rev"),
                            rs.getBigDecimal("cost")),
                    start, end);
            return foldDailyToWeekly(daily);
        } catch (Exception e) {
            log.warn("查询周聚合失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 把「按天」聚合折叠成「按周（周一起始）」聚合 —— **纯函数**，单测直接覆盖。
     *
     * <p>语义与 MySQL 的 {@code DATE_SUB(DATE(d), INTERVAL WEEKDAY(d) DAY)} 完全一致：
     * {@code WEEKDAY()} 周一=0…周日=6，即「该日期所在周的周一」；
     * {@code previousOrSame(MONDAY)} 对周一返回自身、对周日返回前一周一。</p>
     *
     * <p>📌 舍入时机与原 SQL 一致：先累加**未舍入**的日值，最后才 {@link #nz} 到两位小数
     * （不是「先舍入再累加」）。</p>
     */
    static List<WeeklyAggregate> foldDailyToWeekly(List<DailyRevenueCost> daily) {
        Map<LocalDate, BigDecimal[]> agg = new LinkedHashMap<>();
        for (DailyRevenueCost d : daily) {
            LocalDate weekStart = d.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            BigDecimal[] acc = agg.computeIfAbsent(weekStart,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(d.revenue() == null ? BigDecimal.ZERO : d.revenue());
            acc[1] = acc[1].add(d.cost() == null ? BigDecimal.ZERO : d.cost());
        }
        List<WeeklyAggregate> out = new ArrayList<>(agg.size());
        for (Map.Entry<LocalDate, BigDecimal[]> e : agg.entrySet()) {
            out.add(new WeeklyAggregate(e.getKey(), nz(e.getValue()[0]), nz(e.getValue()[1])));
        }
        return out;
    }

    /** 按天的营收 / 成本（折周前的中间结构）。 */
    record DailyRevenueCost(LocalDate date, BigDecimal revenue, BigDecimal cost) {}

    /**
     * 时间区间内各商品的营收与成本
     */
    public List<ProductAggregate> getProductAggregates(LocalDate start, LocalDate end, int limit) {
        int max = limit <= 0 ? 10 : Math.min(limit, 100);
        try {
            return jdbcTemplate.query(
                    "SELECT oi.internal_sku AS sku, "
                            + "COALESCE(p.product_name, oi.product_name) AS pname, "
                            + "SUM(oi.quantity) AS sold, "
                            + "SUM(oi.total_amount) AS rev, "
                            + "COALESCE(SUM(oi.quantity * s.cost_price), 0) AS cost "
                            + "FROM erp_order.t_order_item oi "
                            + "INNER JOIN erp_order.t_order o ON o.id = oi.order_id AND o.deleted = 0 "
                            + "LEFT JOIN erp_product.t_product_sku s ON s.sku_code = oi.internal_sku AND s.deleted = 0 "
                            + "LEFT JOIN erp_product.t_product p ON p.id = s.product_id AND p.deleted = 0 "
                            + "WHERE oi.deleted = 0 AND DATE(o.create_time) >= ? "
                            + "AND DATE(o.create_time) <= ? "
                            + "GROUP BY oi.internal_sku, COALESCE(p.product_name, oi.product_name) "
                            + "ORDER BY rev DESC LIMIT " + max,
                    (rs, i) -> new ProductAggregate(
                            rs.getString("sku"),
                            rs.getString("pname"),
                            rs.getLong("sold"),
                            nz(rs.getBigDecimal("rev")),
                            nz(rs.getBigDecimal("cost"))),
                    start, end);
        } catch (Exception e) {
            log.warn("查询商品 ROI 失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- Top 商品

    /**
     * Top 商品（按营收排序）
     *
     * <p>联 {@code erp_order.t_order_item} + {@code erp_product.t_product}，
     * 跨库 JOIN。</p>
     */
    public List<ProductAggregate> getTopProducts(int limit) {
        int max = limit <= 0 ? 10 : Math.min(limit, 100);
        try {
            return jdbcTemplate.query(
                    "SELECT oi.internal_sku AS sku, "
                            + "COALESCE(p.product_name, oi.product_name) AS pname, "
                            + "SUM(oi.quantity) AS sold, "
                            + "SUM(oi.total_amount) AS rev, "
                            + "COALESCE(SUM(oi.quantity * s.cost_price), 0) AS cost "
                            + "FROM erp_order.t_order_item oi "
                            + "LEFT JOIN erp_product.t_product_sku s ON s.sku_code = oi.internal_sku AND s.deleted = 0 "
                            + "LEFT JOIN erp_product.t_product p ON p.id = s.product_id AND p.deleted = 0 "
                            + "INNER JOIN erp_order.t_order o ON o.id = oi.order_id AND o.deleted = 0 "
                            + "WHERE oi.deleted = 0 "
                            + "GROUP BY oi.internal_sku, COALESCE(p.product_name, oi.product_name) "
                            + "ORDER BY rev DESC LIMIT " + max,
                    (rs, i) -> new ProductAggregate(
                            rs.getString("sku"),
                            rs.getString("pname"),
                            rs.getLong("sold"),
                            nz(rs.getBigDecimal("rev")),
                            nz(rs.getBigDecimal("cost"))));
        } catch (Exception e) {
            log.warn("查询 Top 商品失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- 最近订单

    /**
     * 最近 N 笔订单
     */
    public List<RecentOrder> getRecentOrders(int limit) {
        int max = limit <= 0 ? 10 : Math.min(limit, 100);
        try {
            return jdbcTemplate.query(
                    // 注意：取 internal_order_no（内部订单号）而不是主键 id。
                    // 前端这一列的表头是「订单号」，给主键 id 的话显示成 1/2/3 这种自增数字，
                    // 和用户在订单列表里看到的订单号对不上。
                    "SELECT internal_order_no, platform, status, order_amount, create_time "
                            + "FROM erp_order.t_order WHERE deleted = 0 "
                            + "ORDER BY create_time DESC LIMIT " + max,
                    (rs, i) -> new RecentOrder(
                            rs.getString("internal_order_no"),
                            rs.getString("platform"),
                            rs.getString("status"),
                            nz(rs.getBigDecimal("order_amount")),
                            rs.getTimestamp("create_time").toLocalDateTime()));
        } catch (Exception e) {
            log.warn("查询最近订单失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- 客户分布

    /**
     * 按收货国家聚合客户数与营收
     */
    public List<RegionAggregate> getCustomerDistribution() {
        try {
            return jdbcTemplate.query(
                    "SELECT COALESCE(recipient_country, 'UNKNOWN') AS region, "
                            + "COUNT(DISTINCT buyer_email) AS buyers, "
                            + "COALESCE(SUM(order_amount), 0) AS rev "
                            + "FROM erp_order.t_order WHERE deleted = 0 "
                            + "GROUP BY COALESCE(recipient_country, 'UNKNOWN') "
                            + "ORDER BY rev DESC",
                    (rs, i) -> new RegionAggregate(
                            rs.getString("region"),
                            rs.getLong("buyers"),
                            nz(rs.getBigDecimal("rev"))));
        } catch (Exception e) {
            log.warn("查询客户分布失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- 库存预警

    /**
     * 低库存商品（可用量 ≤ 安全库存）
     */
    public List<StockAlert> getStockAlerts(int limit) {
        int max = limit <= 0 ? 20 : Math.min(limit, 200);
        try {
            return jdbcTemplate.query(
                    // ⚠️ 这里必须查 erp_inventory.erp_inventory（活表），不能查 t_inventory。
                    //    t_inventory 是 init.sql 里的遗留表，全项目没有任何 Java 代码写它，
                    //    只有 seed-data.sql 灌了一次种子数据 → 库存一变它就永远停在种子值，
                    //    看板上的库存预警会变成「看起来真、其实是化石」的假数据。
                    //    erp-inventory-service 的实体 @TableName("erp_inventory") 指向的才是活表。
                    //
                    //    判定粒度必须是「每条库存记录（商品 × 仓库）」，不能按商品跨仓聚合：
                    //    活表唯一键是 (product_id, warehouse_id)，库存服务自己的
                    //    InventoryServiceImpl.checkAndCreateAlert() 也是逐行判定的
                    //    （available == 0 → DANGER，available < safety → WARNING）。
                    //    若按商品 SUM 各仓再比，会出现「某仓已缺货，但被其它仓的库存平均掉」
                    //    的漏报 —— 实测汉堡仓智能手表可用 18 < 安全库存 25 会被聚合吞掉。
                    "SELECT p.internal_sku AS sku, COALESCE(p.product_name, i.sku_code) AS pname, "
                            + "i.available_quantity AS avail, i.safety_stock AS safety "
                            + "FROM erp_inventory.erp_inventory i "
                            + "LEFT JOIN erp_product.t_product p "
                            + "  ON p.id = i.product_id AND p.deleted = 0 "
                            + "WHERE i.available_quantity < i.safety_stock "
                            + "ORDER BY (i.available_quantity - i.safety_stock) ASC LIMIT " + max,
                    (rs, i) -> {
                        int avail = rs.getInt("avail");
                        int safety = rs.getInt("safety");
                        // 与库存服务的判定对齐：可用量归零 → CRITICAL，否则 LOW。
                        String level = (avail <= 0) ? "CRITICAL" : "LOW";
                        return new StockAlert(rs.getString("sku"), rs.getString("pname"),
                                avail, safety, level);
                    });
        } catch (Exception e) {
            log.warn("查询库存预警失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- 其它

    /**
     * 在售商品数
     */
    public int getActiveProductCount() {
        try {
            Integer v = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM erp_product.t_product WHERE deleted = 0 AND status = 1",
                    Integer.class);
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.warn("查询在售商品数失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 低库存商品数
     */
    public int getLowStockProductCount() {
        try {
            Integer v = jdbcTemplate.queryForObject(
                    // ⚠️ 同上：查活表 erp_inventory，不是遗留表 t_inventory。
                    //    口径与 getStockAlerts 一致：按「商品 × 仓库」逐条统计低库存记录数。
                    //    ⚠️ 这里是 COUNT(*)（记录数）而不是 COUNT(DISTINCT product_id)：
                    //    同一个商品可能在多个仓同时低库存，统计「商品数」会与告警列表条数对不上，
                    //    看板会出现「列表有 3 条但数字显示 1」的错觉。
                    "SELECT COUNT(*) FROM erp_inventory.erp_inventory i "
                            + "WHERE i.available_quantity < i.safety_stock", Integer.class);
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.warn("查询低库存商品数失败: {}", e.getMessage());
            return 0;
        }
    }

    // ---------------------------------------------------------------- 今日订单数

    /**
     * 今日订单数
     *
     * <p>看板「今日订单」卡片要的是<b>今天的订单笔数</b>，
     * 不是今天的订单金额（那是 {@code todaySales}）。
     * 之前 overview 里只有金额没有笔数，前端要么自己编、要么拿别的数顶替。</p>
     */
    public long getTodayOrderCount() {
        try {
            // 📌 今天的窗口用参数传（不要 CURDATE()，H2 不认）
            LocalDate day = LocalDate.now();
            Long v = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM erp_order.t_order "
                            + "WHERE deleted = 0 AND create_time >= ? AND create_time < ?",
                    Long.class, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.warn("查询今日订单数失败: {}", e.getMessage());
            return 0;
        }
    }

    // ---------------------------------------------------------------- 订单状态分布

    /**
     * 订单状态分布
     *
     * <p>看板「订单状态分布」图要的是<b>各真实状态的订单数</b>。
     * 之前 overview 只给了 pending/shipped/completed 三个合计数，
     * 而且分组口径还不一致（pending 把 PENDING 和 PROCESSING 混在一起），
     * 画出来的柱子只能靠编。</p>
     *
     * <p>状态取值与 {@code erp-order-service} 的 {@code OrderStatus} 枚举一致：
     * PENDING / CONFIRMED / PROCESSING / SHIPPED / DELIVERED / COMPLETED /
     * CANCELLED / REFUNDED。</p>
     */
    public List<OrderStatusCount> getOrderStatusDistribution() {
        try {
            return jdbcTemplate.query(
                    "SELECT status, COUNT(*) AS cnt FROM erp_order.t_order "
                            + "WHERE deleted = 0 GROUP BY status ORDER BY cnt DESC",
                    (rs, i) -> new OrderStatusCount(rs.getString("status"), rs.getLong("cnt")));
        } catch (Exception e) {
            log.warn("查询订单状态分布失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 待支付金额
     */
    public BigDecimal getPendingPaymentAmount() {
        try {
            BigDecimal v = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(order_amount), 0) FROM erp_order.t_order "
                            + "WHERE deleted = 0 AND payment_status IN ('PENDING','UNPAID','CREATED')",
                    BigDecimal.class);
            return nz(v);
        } catch (Exception e) {
            log.warn("查询待支付金额失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 退款金额
     */
    public BigDecimal getRefundAmount() {
        try {
            BigDecimal v = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(order_amount), 0) FROM erp_order.t_order "
                            + "WHERE deleted = 0 AND status IN ('REFUNDED','CANCELLED')",
                    BigDecimal.class);
            return nz(v);
        } catch (Exception e) {
            log.warn("查询退款金额失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 商品成本合计（用于算毛利）
     *
     * <p>用 {@code erp_product.t_product_sku.cost_price} × 实际销量聚合。</p>
     */
    public BigDecimal getTotalCost() {
        try {
            BigDecimal v = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(s.cost_price * oi.quantity), 0) "
                            + "FROM erp_order.t_order_item oi "
                            + "INNER JOIN erp_order.t_order o ON o.id = oi.order_id AND o.deleted = 0 "
                            + "LEFT JOIN erp_product.t_product_sku s ON s.sku_code = oi.internal_sku AND s.deleted = 0 "
                            + "WHERE oi.deleted = 0",
                    BigDecimal.class);
            return nz(v);
        } catch (Exception e) {
            log.warn("查询成本合计失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 某个 SKU 的历史销量（按天）
     *
     * <p>给销售预测用。SKU 在 {@code erp_product.t_product_sku.sku_code}，
     * 明细里存的是 {@code internal_sku}，两边都要匹配。</p>
     */
    public List<DailyAggregate> getSkuDailySales(String sku, int days) {
        int span = days <= 0 ? 30 : Math.min(days, 365);
        LocalDate from = LocalDate.now().minusDays(span - 1L);
        try {
            return jdbcTemplate.query(
                    "SELECT DATE(o.create_time) AS d, SUM(oi.quantity) AS qty, "
                            + "COALESCE(SUM(oi.total_amount), 0) AS rev "
                            + "FROM erp_order.t_order_item oi "
                            + "INNER JOIN erp_order.t_order o ON o.id = oi.order_id AND o.deleted = 0 "
                            + "LEFT JOIN erp_product.t_product_sku s ON s.sku_code = oi.internal_sku AND s.deleted = 0 "
                            + "WHERE oi.deleted = 0 "
                            + "AND (oi.internal_sku = ? OR oi.platform_sku = ? OR s.sku_code = ?) "
                            + "AND DATE(o.create_time) >= ? "
                            + "GROUP BY DATE(o.create_time) ORDER BY d",
                    (rs, i) -> new DailyAggregate(
                            rs.getDate("d").toLocalDate(),
                            rs.getLong("qty"),
                            nz(rs.getBigDecimal("rev"))),
                    sku, sku, sku, from);
        } catch (Exception e) {
            log.warn("查询 SKU {} 历史销量失败: {}", sku, e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- 竞品

    /**
     * 某个 SKU 的竞品列表
     *
     * <p>数据来自 {@code erp_pricing.competitor_product}（定价服务抓取的真实竞品）。
     * 按 product_code 关联。</p>
     */
    public List<CompetitorRow> getCompetitors(String sku, int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 200);
        try {
            return jdbcTemplate.query(
                    "SELECT c.competitor_name AS cname, c.platform AS plat, c.price AS price, "
                            + "c.rating AS rating, c.review_count AS rc, c.sales_volume AS sv "
                            + "FROM erp_pricing.competitor_product c "
                            + "INNER JOIN erp_pricing.product p ON p.id = c.product_id "
                            // 前端传进来的 productId 可能是 product_code(PROD-001) 也可能是 sku(SKU-BLU-001)，
                            // 两种都匹配上，避免前端传哪种都查不到。
                            + "WHERE p.product_code = ? OR p.sku = ? "
                            + "ORDER BY c.price ASC LIMIT " + max,
                    (rs, i) -> new CompetitorRow(
                            rs.getString("cname"),
                            rs.getString("plat"),
                            nz(rs.getBigDecimal("price")),
                            rs.getBigDecimal("rating") == null ? null
                                    : rs.getBigDecimal("rating").setScale(2, RoundingMode.HALF_UP),
                            rs.getLong("rc"),
                            rs.getLong("sv")),
                    sku, sku);
        } catch (Exception e) {
            log.warn("查询竞品失败 sku={}: {}", sku, e.getMessage());
            return List.of();
        }
    }

    /**
     * 某个 SKU 的当前售价（用于算价差）
     */
    public ProductPrice getProductPrice(String sku) {
        try {
            List<ProductPrice> rows = jdbcTemplate.query(
                    "SELECT product_code AS code, selling_price AS price "
                            + "FROM erp_pricing.product WHERE product_code = ? OR sku = ? LIMIT 1",
                    (rs, i) -> new ProductPrice(
                            rs.getString("code"),
                            nz(rs.getBigDecimal("price"))),
                    sku, sku);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("查询商品售价失败 sku={}: {}", sku, e.getMessage());
            return null;
        }
    }

    /**
     * 某个 SKU 的自身销量（用于市场占有率）
     */
    public long getProductSalesCount(String sku) {
        try {
            Long v = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(oi.quantity), 0) "
                            + "FROM erp_order.t_order_item oi "
                            + "INNER JOIN erp_order.t_order o ON o.id = oi.order_id AND o.deleted = 0 "
                            + "WHERE oi.deleted = 0 AND oi.internal_sku = ?",
                    Long.class, sku);
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.warn("查询商品销量失败 sku={}: {}", sku, e.getMessage());
            return 0;
        }
    }

    // ---------------------------------------------------------------- 工具

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }
}
