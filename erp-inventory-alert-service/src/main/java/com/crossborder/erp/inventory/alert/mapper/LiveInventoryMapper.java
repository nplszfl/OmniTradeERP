package com.crossborder.erp.inventory.alert.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 活库存读取（跨 schema）
 *
 * 📌 为什么必须跨 schema：
 *   本服务自己的库是 {@code erp_inventory_alert}，但**真正的库存数据在
 *   {@code erp_inventory.erp_inventory}**（由 erp-inventory-service 维护）。
 *   预警引擎必须基于活库存判定，否则就是拿空表算库存 —— 结果永远是「一切正常」。
 *
 * 📌 仓库编码的坑：
 *   {@code erp_inventory.warehouse_id} 是 **BIGINT 主键**，
 *   而 {@code t_inventory_alert_rule.warehouse_id} 是 **VARCHAR(50) 仓库编码**
 *   （如 'WH-DE-HH'）。两者对不上 —— 必须 JOIN {@code erp_warehouse.wms_warehouse}
 *   拿到 {@code warehouse_code} 才能和规则匹配。
 *
 * 📌 与 analytics 的做法一致：同一个数据源跨库查（analytics 查 erp_order 库，
 *   却查 erp_inventory / erp_product / erp_pricing）。
 *
 * ⚠️ **禁止在 SQL 里 JOIN wms_warehouse.warehouse_code 和
 *    t_inventory_alert_rule.warehouse_id**：
 *    前者 collation 是 utf8mb4_unicode_ci，后者是 utf8mb4_0900_ai_ci，
 *    直接比较会报 ERROR 1267 "Illegal mix of collations"。
 *    所以这里只把 warehouse_code **查出来**，与规则的匹配放在 Java 侧做
 *    （见 InventoryAlertService.checkAndAlert）。
 */
@Mapper
public interface LiveInventoryMapper {

    /**
     * 扫描全部活库存，返回预警判定所需的最小字段集。
     *
     * 📌 用 available_quantity（可用量）而不是 quantity（总量）：
     *    锁定中的库存不能用于发货，用总量会把「锁着的库存」误判成充足。
     *    （与 inventory-service 的 getLowStockInventory 口径一致）
     *
     * 📌 sku / product_name / warehouse_name 全部 COALESCE：
     *    t_inventory_alert.sku 是 NOT NULL，源表 sku_code 可能是 NULL，
     *    不兜底会直接插库失败（整个扫描被一条脏数据打断）。
     */
    @Select("""
            SELECT i.product_id                   AS productId,
                   COALESCE(i.sku_code, '')         AS sku,
                   COALESCE(p.product_name, '')     AS productName,
                   COALESCE(w.warehouse_code, '')   AS warehouseCode,
                   COALESCE(w.name, '')             AS warehouseName,
                   i.available_quantity             AS currentStock,
                   i.safety_stock                   AS safetyStock
            FROM erp_inventory.erp_inventory i
            LEFT JOIN erp_warehouse.wms_warehouse w ON w.id = i.warehouse_id
            LEFT JOIN erp_product.t_product p ON p.id = i.product_id
            """)
    List<Map<String, Object>> selectLiveInventory();
}
