package com.crossborder.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.inventory.entity.Inventory;
import com.crossborder.erp.inventory.entity.InventoryLog;
import com.crossborder.erp.inventory.enums.InventoryStatus;
import com.crossborder.erp.inventory.service.InventoryService;
import com.crossborder.erp.inventory.vo.InventoryBatchOperation;
import com.crossborder.erp.inventory.vo.InventoryQuery;
import com.crossborder.erp.inventory.vo.InventoryStatistics;
import com.crossborder.erp.inventory.vo.InventoryTrend;
import com.crossborder.erp.inventory.vo.BatchOperationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存服务 Controller
 *
 * <p>路径与前端契约 {@code erp-web/src/api/inventory.ts} 对齐：
 * <pre>
 * GET  /inventory/stats              库存概览（前端直接消费对象）
 * GET  /inventory/list               分页查询（前端消费 data.records / data.total）
 * GET  /inventory/{id}               库存详情
 * GET  /inventory/{inventoryId}/flows 库存流水分页（前端消费 data.records）
 * POST /inventory/adjust             调整库存
 * POST /inventory/transfer           调拨库存
 * POST /inventory/import             批量导入库存
 * POST /inventory/increase           入库
 * POST /inventory/decrease           出库
 * GET  /inventory/statistics         库存统计
 * GET  /inventory/trend              库存趋势
 * </pre>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Slf4j
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 库存概览（前端：inventoryApi.getStats → {@code res || {...}}）
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        InventoryStatistics statistics = inventoryService.getStatistics(null, null, null);

        Map<String, Object> result = new HashMap<>();
        result.put("totalSku", statistics.getTotalSku());
        result.put("normalStock", statistics.getNormalCount());
        result.put("lowStock", statistics.getWarningCount());
        result.put("outOfStock", statistics.getDangerCount());
        result.put("totalQuantity", statistics.getTotalQuantity());
        result.put("availableQuantity", statistics.getAvailableQuantity());
        result.put("lockedQuantity", statistics.getLockedQuantity());
        result.put("totalAmount", statistics.getTotalAmount());
        return result;
    }

    /**
     * 分页查询库存（前端：inventoryApi.getInventoryList → {@code res.data.records}）
     */
    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String skuCode,
            @RequestParam(required = false) String status) {

        InventoryQuery query = new InventoryQuery();
        query.setPage(current);
        query.setSize(size);
        query.setWarehouseId(warehouseId);
        query.setSkuCode(skuCode);
        if (status != null && !status.isEmpty()) {
            query.setStatus(parseStatus(status));
        }

        Page<Inventory> inventoryPage = inventoryService.pageInventories(query);

        Page<Map<String, Object>> page = new Page<>(inventoryPage.getCurrent(), inventoryPage.getSize(),
                inventoryPage.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Inventory inventory : inventoryPage.getRecords()) {
            records.add(toInventoryVO(inventory));
        }
        page.setRecords(records);
        return Result.success(page);
    }

    /**
     * 库存导出（前端：inventoryApi.exportInventory → {@code responseType: 'blob'}）
     *
     * <p>⚠️ 必须返<b>裸字节流</b>，不能包 Result：
     * 前端用 {@code responseType: 'blob'} 下载，包一层 JSON 的话
     * 下载到的文件里全是 {@code {"code":200,"data":"UEsDBB..."}} 这种文本。</p>
     *
     * <p>用 CSV + UTF-8 BOM（Excel 双击能正确识别中文），不引 easyexcel，
     * 避免为了一个导出再拉一遍 POI 依赖树。</p>
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String skuCode,
            @RequestParam(required = false) String status) {

        InventoryQuery query = new InventoryQuery();
        query.setPage(1);
        query.setSize(10000);
        query.setWarehouseId(warehouseId);
        query.setSkuCode(skuCode);
        if (status != null && !status.isEmpty()) {
            query.setStatus(parseStatus(status));
        }

        Page<Inventory> page = inventoryService.pageInventories(query);

        StringBuilder sb = new StringBuilder();
        // BOM：没有它 Excel 打开中文全是乱码
        sb.append('﻿');
        sb.append("SKU,商品ID,仓库ID,库位ID,总数量,可用数量,锁定数量,安全库存,单位成本,状态,更新时间\n");
        for (Inventory inv : page.getRecords()) {
            sb.append(csv(inv.getSkuCode())).append(',')
              .append(csv(inv.getProductId())).append(',')
              .append(csv(inv.getWarehouseId())).append(',')
              .append(csv(inv.getLocationId())).append(',')
              .append(csv(inv.getQuantity())).append(',')
              .append(csv(inv.getAvailableQuantity())).append(',')
              .append(csv(inv.getLockedQuantity())).append(',')
              .append(csv(inv.getSafetyStock())).append(',')
              .append(csv(inv.getUnitCost())).append(',')
              .append(csv(inv.getStatus() == null ? null : inv.getStatus().name())).append(',')
              .append(csv(inv.getUpdateTime()))
              .append('\n');
        }

        byte[] body = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "inventory-" + System.currentTimeMillis() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    /**
     * CSV 字段转义：含逗号/引号/换行的要加引号，引号翻倍
     */
    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * 库存详情（前端：inventoryApi.getInventoryDetail）
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Inventory inventory = inventoryService.getInventoryById(id);
        if (inventory == null) {
            throw new BizException("库存记录不存在");
        }
        return Result.success(toInventoryVO(inventory));
    }

    /**
     * 库存流水分页（前端：inventoryApi.getStockFlows → {@code res.data.records}）
     */
    @GetMapping("/{inventoryId}/flows")
    public Result<Page<Map<String, Object>>> flows(
            @PathVariable Long inventoryId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {

        List<InventoryLog> logs = inventoryService.getInventoryLogs(inventoryId, current, size);

        Page<Map<String, Object>> page = new Page<>(current, size, logs.size());
        List<Map<String, Object>> records = new ArrayList<>();
        for (InventoryLog log : logs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("flowTime", log.getCreateTime());
            item.put("flowType", log.getOperationType() == null
                    ? null : log.getOperationType().name().toLowerCase(java.util.Locale.ROOT));
            item.put("quantity", log.getQuantity());
            item.put("orderNo", log.getOrderNo());
            item.put("remark", log.getRemark());
            item.put("beforeQuantity", log.getBeforeQuantity());
            item.put("afterQuantity", log.getAfterQuantity());
            records.add(item);
        }
        page.setRecords(records);
        return Result.success(page);
    }

    /**
     * 入库
     */
    @PostMapping("/increase")
    public Result<Void> increase(@RequestBody Map<String, Object> body) {
        inventoryService.increaseInventory(
                asLong(body.get("productId")), asLong(body.get("warehouseId")),
                asInt(body.get("quantity")), asString(body.get("remark")));
        return Result.success();
    }

    /**
     * 出库
     */
    @PostMapping("/decrease")
    public Result<Void> decrease(@RequestBody Map<String, Object> body) {
        inventoryService.decreaseInventory(
                asLong(body.get("productId")), asLong(body.get("warehouseId")),
                asInt(body.get("quantity")), asString(body.get("remark")));
        return Result.success();
    }

    /**
     * 调整库存（前端：inventoryApi.adjustStock）
     *
     * <p>type 为 increase / decrease / set：set 表示直接设定到指定数量。</p>
     */
    @PostMapping("/adjust")
    public Result<Void> adjust(@RequestBody Map<String, Object> body) {
        Long inventoryId = asLong(body.get("inventoryId"));
        String type = asString(body.get("type"));
        Integer quantity = asInt(body.get("quantity"));
        String remark = asString(body.get("remark"));

        Inventory inventory = inventoryService.getInventoryById(inventoryId);
        if (inventory == null) {
            throw new BizException("库存记录不存在");
        }
        Long productId = inventory.getProductId();
        Long warehouseId = inventory.getWarehouseId();
        int current = inventory.getQuantity() == null ? 0 : inventory.getQuantity();

        if ("set".equalsIgnoreCase(type)) {
            int diff = quantity - current;
            if (diff > 0) {
                inventoryService.increaseInventory(productId, warehouseId, diff, remark);
            } else if (diff < 0) {
                inventoryService.decreaseInventory(productId, warehouseId, -diff, remark);
            }
        } else if ("decrease".equalsIgnoreCase(type)) {
            inventoryService.decreaseInventory(productId, warehouseId, quantity, remark);
        } else {
            inventoryService.increaseInventory(productId, warehouseId, quantity, remark);
        }
        return Result.success();
    }

    /**
     * 调拨库存（前端：inventoryApi.transferStock）
     */
    @PostMapping("/transfer")
    public Result<Void> transfer(@RequestBody Map<String, Object> body) {
        Long fromInventoryId = asLong(body.get("fromInventoryId"));
        Inventory from = inventoryService.getInventoryById(fromInventoryId);
        if (from == null) {
            throw new BizException("源库存记录不存在");
        }
        inventoryService.transferInventory(from.getProductId(), from.getWarehouseId(),
                asLong(body.get("toWarehouseId")), asInt(body.get("quantity")),
                asString(body.get("remark")));
        return Result.success();
    }

    /**
     * 批量导入库存（前端：inventoryApi.importInventory）
     */
    @PostMapping("/import")
    public Result<BatchOperationResult> importInventory(@RequestBody List<Map<String, Object>> rows) {
        List<InventoryBatchOperation> operations = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            InventoryBatchOperation operation = new InventoryBatchOperation();
            operation.setProductId(asLong(row.get("productId")));
            operation.setWarehouseId(asLong(row.get("warehouseId")));
            operation.setQuantity(asInt(row.get("quantity")));
            operation.setRemark(asString(row.get("remark")));
            operations.add(operation);
        }
        return Result.success(inventoryService.batchIncreaseInventory(operations));
    }

    /**
     * 库存统计
     */
    @GetMapping("/statistics")
    public Result<InventoryStatistics> statistics(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(inventoryService.getStatistics(warehouseId,
                parseDateTime(startDate, false), parseDateTime(endDate, true)));
    }

    /**
     * 库存趋势
     */
    @GetMapping("/trend")
    public Result<List<InventoryTrend>> trend(
            @RequestParam Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDate end = endDate == null || endDate.isEmpty() ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate start = startDate == null || startDate.isEmpty()
                ? end.minusDays(29) : LocalDate.parse(startDate);
        return Result.success(inventoryService.getInventoryTrend(productId, warehouseId, start, end));
    }

    // ==================== 内部辅助 ====================

    /**
     * 库存 → 前端字段（前端 {@code erp-web/src/api/inventory.ts} 的 {@code Inventory}）
     */
    private Map<String, Object> toInventoryVO(Inventory inventory) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", inventory.getId());
        item.put("skuCode", inventory.getSkuCode());
        item.put("productId", inventory.getProductId());
        item.put("warehouseId", inventory.getWarehouseId());
        item.put("locationId", inventory.getLocationId());
        item.put("totalQty", inventory.getQuantity());
        item.put("availableQty", inventory.getAvailableQuantity());
        item.put("lockedQty", inventory.getLockedQuantity());
        item.put("safetyStock", inventory.getSafetyStock());
        item.put("unitCost", inventory.getUnitCost());
        item.put("status", inventory.getStatus() == null
                ? null : inventory.getStatus().name().toLowerCase(java.util.Locale.ROOT));
        item.put("updateTime", inventory.getUpdateTime());
        return item;
    }

    private static InventoryStatus parseStatus(String status) {
        for (InventoryStatus value : InventoryStatus.values()) {
            if (value.name().equalsIgnoreCase(status)) {
                return value;
            }
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDateTime parseDateTime(String date, boolean endOfDay) {
        if (date == null || date.isEmpty()) {
            return null;
        }
        LocalDate localDate = LocalDate.parse(date);
        return endOfDay ? localDate.atTime(23, 59, 59) : localDate.atStartOfDay();
    }
}
