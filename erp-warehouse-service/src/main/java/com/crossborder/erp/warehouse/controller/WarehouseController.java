package com.crossborder.erp.warehouse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.warehouse.dto.LocationQueryDTO;
import com.crossborder.erp.warehouse.dto.WarehouseQueryDTO;
import com.crossborder.erp.warehouse.entity.Warehouse;
import com.crossborder.erp.warehouse.entity.WarehouseLocation;
import com.crossborder.erp.warehouse.service.WarehouseService;
import com.crossborder.erp.warehouse.vo.LocationVO;
import com.crossborder.erp.warehouse.vo.WarehouseDashboardVO;
import com.crossborder.erp.warehouse.vo.WarehouseStatisticsVO;
import com.crossborder.erp.warehouse.vo.WarehouseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 仓库与库位 Controller
 *
 * <p>路径与前端契约 {@code erp-web/src/api/warehouse.ts} 对齐：
 * <pre>
 * GET    /warehouse/list                        分页查询（current/size/keyword/status）
 * GET    /warehouse/all                         启用仓库列表
 * GET    /warehouse/{warehouseId}               仓库详情
 * POST   /warehouse                             创建仓库
 * PUT    /warehouse                             更新仓库
 * DELETE /warehouse/{warehouseId}               删除仓库
 * GET    /warehouse/{warehouseId}/locations     仓库库位列表（前端直接消费数组）
 * POST   /warehouse/location                    创建库位
 * PUT    /warehouse/location                    更新库位
 * DELETE /warehouse/location/{locationId}       删除库位
 * </pre>
 *
 * <p>业务异常统一由 erp-common 的 {@code GlobalExceptionHandler} 处理，Controller 层不做异常包装。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Slf4j
@RestController
@RequestMapping("/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    // ==================== 仓库管理 ====================

    /**
     * 分页查询仓库（前端：warehouseApi.getWarehouseList）
     *
     * <p>入参 current/size 对应 DTO 的 page/size；keyword 同时匹配仓库编码与名称。</p>
     */
    @GetMapping("/list")
    public Result<Page<WarehouseVO>> listWarehouses(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String warehouseType) {

        WarehouseQueryDTO query = new WarehouseQueryDTO();
        query.setPage(current);
        query.setSize(size);
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setWarehouseCode(warehouseCode);
        query.setName(name);
        query.setCountry(country);
        query.setCity(city);
        query.setWarehouseType(warehouseType);

        log.info("分页查询仓库: current={}, size={}, keyword={}", current, size, keyword);

        Page<Warehouse> page = warehouseService.queryWarehouses(query);
        Page<WarehouseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(WarehouseVO::from)
                .collect(Collectors.toList()));
        return Result.success(voPage);
    }

    /**
     * 查询启用状态下的仓库列表。
     *
     * <p>前端 {@code warehouseApi.getWarehouses} 直接把响应体当数组用
     * （{@code warehouses.value = res || []}），因此返回裸数组而非 Result 包装。</p>
     */
    @GetMapping("/all")
    public List<WarehouseVO> listAllWarehouses() {
        return warehouseService.listWarehouses().stream()
                .map(WarehouseVO::from)
                .collect(Collectors.toList());
    }

    /**
     * 查询仓库详情
     */
    @GetMapping("/{warehouseId}")
    public Result<WarehouseVO> getWarehouse(@PathVariable Long warehouseId) {
        return Result.success(WarehouseVO.from(warehouseService.getWarehouse(warehouseId)));
    }

    /**
     * 创建仓库
     */
    @PostMapping
    public Result<WarehouseVO> createWarehouse(@RequestBody WarehouseVO warehouse) {
        log.info("创建仓库请求: {}", warehouse.getWarehouseName());
        Long warehouseId = warehouseService.createWarehouse(warehouse.toEntity());
        return Result.success(WarehouseVO.from(warehouseService.getWarehouse(warehouseId)));
    }

    /**
     * 更新仓库（主键取自请求体的 id）
     */
    @PutMapping
    public Result<WarehouseVO> updateWarehouse(@RequestBody WarehouseVO warehouse) {
        log.info("更新仓库: {}", warehouse.getId());
        warehouseService.updateWarehouse(warehouse.toEntity());
        return Result.success(WarehouseVO.from(warehouseService.getWarehouse(warehouse.getId())));
    }

    /**
     * 删除仓库（仓库仍有库存时由 Service 抛出 BusinessException）
     */
    @DeleteMapping("/{warehouseId}")
    public Result<Void> deleteWarehouse(@PathVariable Long warehouseId) {
        log.info("删除仓库: {}", warehouseId);
        warehouseService.deleteWarehouse(warehouseId);
        return Result.success();
    }

    /**
     * 查询仓库统计信息
     */
    @GetMapping("/warehouses/{warehouseId}/statistics")
    public Result<WarehouseStatisticsVO> getWarehouseStatistics(@PathVariable Long warehouseId) {
        return Result.success(warehouseService.getWarehouseStatistics(warehouseId));
    }

    /**
     * 查询仓库仪表盘
     */
    @GetMapping("/warehouses/{warehouseId}/dashboard")
    public Result<WarehouseDashboardVO> getWarehouseDashboard(@PathVariable Long warehouseId) {
        return Result.success(warehouseService.getWarehouseDashboard(warehouseId));
    }

    // ==================== 库位管理 ====================

    /**
     * 查询指定仓库下的全部库位
     *
     * <p>前端 {@code warehouseApi.getLocations} 直接把响应体当数组使用
     * （{@code locationList.value = res || []}），因此此处返回裸数组而非 Result 包装。</p>
     */
    @GetMapping("/{warehouseId}/locations")
    public List<LocationVO> getLocationsByWarehouse(@PathVariable Long warehouseId) {
        return warehouseService.getLocationsByWarehouse(warehouseId).stream()
                .map(this::toLocationVO)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询库位
     */
    @GetMapping("/locations/page")
    public Result<Page<LocationVO>> listLocations(LocationQueryDTO query) {
        log.info("分页查询库位: {}", query);
        Page<WarehouseLocation> page = warehouseService.listLocations(query);
        Page<LocationVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(this::toLocationVO)
                .collect(Collectors.toList()));
        return Result.success(voPage);
    }

    /**
     * 查询库位详情
     */
    @GetMapping("/location/{locationId}")
    public Result<LocationVO> getLocation(@PathVariable Long locationId) {
        return Result.success(toLocationVO(warehouseService.getLocation(locationId)));
    }

    /**
     * 查询可用库位（未满仓且可存储）
     */
    @GetMapping("/locations/available")
    public Result<List<LocationVO>> getAvailableLocations(@RequestParam Long warehouseId,
                                                           @RequestParam(required = false) String locationType) {
        return Result.success(warehouseService.getAvailableLocations(warehouseId, locationType).stream()
                .map(this::toLocationVO)
                .collect(Collectors.toList()));
    }

    /**
     * 创建库位
     */
    @PostMapping("/location")
    public Result<LocationVO> createLocation(@RequestBody WarehouseLocation location) {
        log.info("创建库位请求: 仓库={}, 编码={}", location.getWarehouseId(), location.getLocationCode());
        Long locationId = warehouseService.createLocation(normalizeStatus(location));
        return Result.success(toLocationVO(warehouseService.getLocation(locationId)));
    }

    /**
     * 更新库位（主键取自请求体的 id）
     */
    @PutMapping("/location")
    public Result<LocationVO> updateLocation(@RequestBody WarehouseLocation location) {
        log.info("更新库位: {}", location.getId());
        warehouseService.updateLocation(normalizeStatus(location));
        return Result.success(toLocationVO(warehouseService.getLocation(location.getId())));
    }

    /**
     * 删除库位（库位仍有库存时由 Service 抛出 BusinessException）
     */
    @DeleteMapping("/location/{locationId}")
    public Result<Void> deleteLocation(@PathVariable Long locationId) {
        log.info("删除库位: {}", locationId);
        warehouseService.deleteLocation(locationId);
        return Result.success();
    }

    /**
     * 批量创建库位（按 区域-排-列-层 规则生成编码）
     */
    @PostMapping("/locations/batch")
    public Result<Void> batchCreateLocations(@RequestParam Long warehouseId,
                                             @RequestParam String zone,
                                             @RequestParam(defaultValue = "1") int rowCount,
                                             @RequestParam(defaultValue = "1") int columnCount,
                                             @RequestParam(defaultValue = "1") int levelCount) {
        log.info("批量创建库位: 仓库={}, 区域={}, 规格={}排{}列{}层", warehouseId, zone, rowCount, columnCount, levelCount);
        warehouseService.batchCreateLocations(warehouseId, zone, rowCount, columnCount, levelCount);
        return Result.success();
    }

    /**
     * 库位实体 -> 库位VO
     */
    private LocationVO toLocationVO(WarehouseLocation location) {
        if (location == null) {
            return null;
        }
        LocationVO vo = new LocationVO();
        vo.setId(location.getId());
        vo.setWarehouseId(location.getWarehouseId());
        vo.setLocationCode(location.getLocationCode());
        vo.setLocationName(location.getLocationName());
        vo.setLocationType(location.getLocationType());
        vo.setZone(location.getZone());
        vo.setRow(location.getRow());
        vo.setColumn(location.getColumn());
        vo.setLevel(location.getLevel());
        vo.setStockQuantity(location.getStockQuantity());
        vo.setMaxCapacity(location.getMaxCapacity());
        vo.setCurrentCapacity(location.getCurrentCapacity());
        vo.setStatus(toLocationStatus(location.getStatus()));
        vo.setIsPickable(location.getIsPickable());
        vo.setIsStorable(location.getIsStorable());
        vo.setRemark(location.getRemark());
        return vo;
    }

    /**
     * 前端库位状态为数字（1 可用 / 0 不可用），后端领域值为 ACTIVE / INACTIVE，这里做一次归一化。
     */
    private WarehouseLocation normalizeStatus(WarehouseLocation location) {
        if (location == null || location.getStatus() == null) {
            return location;
        }
        if ("1".equals(location.getStatus())) {
            location.setStatus("ACTIVE");
        } else if ("0".equals(location.getStatus())) {
            location.setStatus("INACTIVE");
        }
        return location;
    }

    /**
     * 库位领域状态（ACTIVE / INACTIVE）-&gt; 前端状态（1 可用 / 0 不可用）。
     */
    private Integer toLocationStatus(String status) {
        if (status == null) {
            return null;
        }
        if ("ACTIVE".equalsIgnoreCase(status) || "1".equals(status)) {
            return 1;
        }
        if ("INACTIVE".equalsIgnoreCase(status) || "0".equals(status)) {
            return 0;
        }
        try {
            return Integer.valueOf(status);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
