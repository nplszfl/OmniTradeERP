package com.crossborder.erp.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存展示名解析器（把 ID 翻译成前端要的名字）
 *
 * <p><b>为什么需要它</b><br>
 * 库存表 {@code erp_inventory.erp_inventory} 是 (product_id, warehouse_id) 粒度，
 * 只存 ID。前端 {@code erp-web/src/api/inventory.ts} 的 {@code Inventory} 接口要的是
 * {@code productName} / {@code warehouseName} / {@code locationCode}，
 * 缺了这三个字段，库存列表的「商品名称 / 仓库 / 库位」三列全是空白，
 * 「按仓库统计」图表也会把全部库存堆到「未知」这一栏。</p>
 *
 * <p><b>为什么用 JdbcTemplate 而不是 Feign</b><br>
 * 这三个名字分别在 {@code erp_product.t_product}、{@code erp_warehouse.wms_warehouse}、
 * {@code erp_warehouse.wms_warehouse_location} 三张表里，
 * 而它们和库存表在<b>同一个 MySQL 实例</b>（compose 里就是同一个 mysql 容器），
 * 直接跨 schema JOIN 最快，也不会引入一次网络往返和降级逻辑。</p>
 *
 * <p><b>降级策略</b><br>
 * 跨 schema 查询失败（比如单测用的 H2 里根本没有 erp_product / erp_warehouse 这两个库），
 * 不能让整个库存列表接口挂掉 —— 这里捕获异常并返回空 Map，
 * 前端那三列显示为空，但接口照常返回 200 和真实库存数据。
 * 这是刻意的取舍：<b>名字是锦上添花，库存数量才是主数据</b>。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryNameResolver {

    private final JdbcTemplate jdbcTemplate;

    /** 商品 ID → 商品名（来自 erp_product.t_product） */
    public Map<Long, String> resolveProductNames(Collection<Long> productIds) {
        Set<Long> ids = productIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = "SELECT id, product_name FROM erp_product.t_product "
                + "WHERE deleted = 0 AND id IN (" + placeholders(ids.size()) + ")";
        return queryNameMap(sql, ids, "商品", "product_name");
    }

    /** 仓库 ID → 仓库名（来自 erp_warehouse.wms_warehouse） */
    public Map<Long, String> resolveWarehouseNames(Collection<Long> warehouseIds) {
        Set<Long> ids = warehouseIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = "SELECT id, name FROM erp_warehouse.wms_warehouse "
                + "WHERE id IN (" + placeholders(ids.size()) + ")";
        return queryNameMap(sql, ids, "仓库", "name");
    }

    /** 库位 ID → 库位编码（来自 erp_warehouse.wms_warehouse_location） */
    public Map<Long, String> resolveLocationCodes(Collection<Long> locationIds) {
        Set<Long> ids = locationIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = "SELECT id, location_code FROM erp_warehouse.wms_warehouse_location "
                + "WHERE id IN (" + placeholders(ids.size()) + ")";
        return queryNameMap(sql, ids, "库位", "location_code");
    }

    /**
     * 从映射里取值（null 安全的 Map.get）
     *
     * <p>⚠️ {@code Map.of()} 返回的是不可变空 Map，
     * 对它调用 {@code get(null)} 会抛 NullPointerException
     * （{@code MapN.get} 内部是 {@code Objects.requireNonNull(key)}）。
     * 而 productId / warehouseId 缺失时 asLong() 返回 null，
     * 所以这里必须自己挡一层。</p>
     */
    private static String lookup(Map<Long, String> map, Long key) {
        if (map == null || key == null) {
            return null;
        }
        return map.get(key);
    }

    /**
     * 补齐一条库存记录的前端展示字段
     *
     * @param vo        前端 VO map（原地修改）
     * @param productNames  商品名映射
     * @param warehouseNames 仓库名映射
     * @param locationCodes 库位编码映射
     */
    public void enrich(Map<String, Object> vo,
                       Map<Long, String> productNames,
                       Map<Long, String> warehouseNames,
                       Map<Long, String> locationCodes) {
        vo.put("productName", lookup(productNames, asLong(vo.get("productId"))));
        vo.put("warehouseName", lookup(warehouseNames, asLong(vo.get("warehouseId"))));
        vo.put("locationCode", lookup(locationCodes, asLong(vo.get("locationId"))));
    }

    /**
     * 批量补齐多条库存记录
     *
     * @param records 库存 VO 列表（原地修改）
     */
    public void enrichAll(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, String> productNames = resolveProductNames(
                records.stream().map(r -> asLong(r.get("productId"))).collect(Collectors.toList()));
        Map<Long, String> warehouseNames = resolveWarehouseNames(
                records.stream().map(r -> asLong(r.get("warehouseId"))).collect(Collectors.toList()));
        Map<Long, String> locationCodes = resolveLocationCodes(
                records.stream().map(r -> asLong(r.get("locationId"))).collect(Collectors.toList()));
        for (Map<String, Object> record : records) {
            enrich(record, productNames, warehouseNames, locationCodes);
        }
    }

    /**
     * 批量补齐多条库存记录（复用已查好的映射，避免重复查库）
     *
     * @param records        库存 VO 列表（原地修改）
     * @param productNames   商品名映射
     * @param warehouseNames 仓库名映射
     * @param locationCodes  库位编码映射
     */
    public void enrichAll(List<Map<String, Object>> records,
                          Map<Long, String> productNames,
                          Map<Long, String> warehouseNames,
                          Map<Long, String> locationCodes) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (Map<String, Object> record : records) {
            enrich(record, productNames, warehouseNames, locationCodes);
        }
    }

    /**
     * 补齐流转指标：日均销量 / 预计可售天数 / 最后入库时间 / 最后出库时间
     *
     * <p>数据来源是本库的 {@code erp_inventory_log}（库存流水表），
     * 不需要跨 schema。</p>
     *
     * <p>口径说明：日均销量 = 最近 30 天出库总量 / 30；
     * 预计可售天数 = 可用库存 / 日均销量。日均销量为 0 时可售天数无意义，
     * 返回 {@code null}（前端渲染成 '-'），而不是 0 或 Infinity。</p>
     */
    public void enrichFlowMetrics(Map<String, Object> vo) {
        Long inventoryId = asLong(vo.get("id"));
        if (inventoryId == null) {
            vo.put("dailySales", BigDecimal.ZERO);
            vo.put("turnoverDays", null);
            vo.put("lastInTime", null);
            vo.put("lastOutTime", null);
            return;
        }

        Map<String, Object> agg = queryFlowAgg(inventoryId);
        BigDecimal outbound30 = agg.get("outbound_30") == null
                ? BigDecimal.ZERO : new BigDecimal(agg.get("outbound_30").toString());
        BigDecimal dailySales = outbound30.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        vo.put("dailySales", dailySales);

        Integer available = vo.get("availableQty") == null ? null
                : Integer.valueOf(vo.get("availableQty").toString());
        if (available == null || dailySales.compareTo(BigDecimal.ZERO) <= 0) {
            vo.put("turnoverDays", null);
        } else {
            vo.put("turnoverDays",
                    BigDecimal.valueOf(available).divide(dailySales, 0, RoundingMode.HALF_UP).intValue());
        }
        vo.put("lastInTime", agg.get("last_in_time"));
        vo.put("lastOutTime", agg.get("last_out_time"));
    }

    /**
     * 汇总某条库存记录的出库量与最后出入库时间
     */
    private Map<String, Object> queryFlowAgg(Long inventoryId) {
        // ⚠️ 时间下界用参数传，不要写 DATE_SUB(NOW(), INTERVAL 30 DAY)：
        //    那是 MySQL 方言，H2（即使 MODE=MySQL）不认，会整条 SQL 抛异常，
        //    结果是整个聚合降级成空 Map —— 日均销量恒为 0、最后出入库时间恒为 null。
        //    用 LocalDateTime 参数则 MySQL 和 H2 都能跑。
        String sql = "SELECT "
                + "  COALESCE(SUM(CASE WHEN operation_type = 'OUTBOUND' "
                + "      AND create_time >= ? THEN ABS(quantity) ELSE 0 END), 0) "
                + "  AS outbound_30, "
                + "  MAX(CASE WHEN operation_type = 'INBOUND'  THEN create_time END) AS last_in_time, "
                + "  MAX(CASE WHEN operation_type = 'OUTBOUND' THEN create_time END) AS last_out_time "
                + "FROM erp_inventory_log WHERE inventory_id = ?";
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql,
                    LocalDateTime.now().minusDays(30), inventoryId);
            return row == null ? new HashMap<>() : row;
        } catch (Exception e) {
            log.debug("查询库存流水汇总失败（inventoryId={}）：{}", inventoryId, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 执行名称查询并转成 Map
     *
     * <p>失败时返回空 Map 而不是抛出：名字缺失不能让库存列表整个 500。</p>
     */
    private Map<Long, String> queryNameMap(String sql, Collection<Long> ids,
                                           String label, String nameColumn) {
        Map<Long, String> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ids.toArray());
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                Object name = row.get(nameColumn);
                if (id instanceof Number && name != null) {
                    result.put(((Number) id).longValue(), name.toString());
                }
            }
        } catch (Exception e) {
            // 典型场景：单测 H2 里没有 erp_product / erp_warehouse 库；
            // 生产场景：跨 schema 权限不足。都不该让库存主数据不可用。
            log.debug("解析{}名称失败（ids={}）：{}", label, ids, e.getMessage());
        }
        return result;
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
