package com.crossborder.erp.warehouse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.warehouse.dto.WaveCreateDTO;
import com.crossborder.erp.warehouse.entity.PackingMaterial;
import com.crossborder.erp.warehouse.entity.WavePicking;
import com.crossborder.erp.warehouse.entity.WavePickingDetail;
import com.crossborder.erp.warehouse.service.WarehouseService;
import lombok.Data;
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

/**
 * 波次拣货与包装材料 Controller
 *
 * <p>覆盖 {@link WarehouseService} 中的波次拣货与包装材料相关方法。</p>
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
public class WavePickingController {

    private final WarehouseService warehouseService;

    // ==================== 波次拣货 ====================

    /**
     * 创建波次（波次主信息使用 WaveCreateDTO，拣货明细单独提交）
     */
    @PostMapping("/waves")
    public Result<Long> createWavePicking(@RequestBody WaveCreateRequest request) {
        WaveCreateDTO waveDto = request.getWave() == null ? new WaveCreateDTO() : request.getWave();
        List<WavePickingDetail> details = request.getDetails() == null ? List.of() : request.getDetails();

        WavePicking wave = new WavePicking();
        wave.setWaveType(waveDto.getWaveType());
        wave.setPriority(waveDto.getPriority());
        wave.setRemark(waveDto.getRemark());

        log.info("创建波次请求: 类型={}, 优先级={}, 订单数={}, 明细行数={}",
                waveDto.getWaveType(), waveDto.getPriority(),
                waveDto.getOrderIds() == null ? 0 : waveDto.getOrderIds().size(), details.size());

        Long waveId = warehouseService.createWavePicking(wave, details);
        return Result.success(waveId);
    }

    /**
     * 查询波次详情
     */
    @GetMapping("/waves/{waveId}")
    public Result<WavePicking> getWavePicking(@PathVariable Long waveId) {
        return Result.success(warehouseService.getWavePicking(waveId));
    }

    /**
     * 分页查询波次列表
     */
    @GetMapping("/waves/page")
    public Result<Page<WavePicking>> listWavePickings(@RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        log.info("分页查询波次: 状态={}", status);
        return Result.success(warehouseService.listWavePickings(status, page, size));
    }

    /**
     * 分配波次给拣货员
     */
    @PostMapping("/waves/{waveId}/assign")
    public Result<Void> assignWavePicking(@PathVariable Long waveId, @RequestParam String userId) {
        log.info("分配波次: 波次={}, 拣货员={}", waveId, userId);
        warehouseService.assignWavePicking(waveId, userId);
        return Result.success();
    }

    /**
     * 开始波次拣货
     */
    @PostMapping("/waves/{waveId}/start")
    public Result<Void> startWavePicking(@PathVariable Long waveId) {
        log.info("开始波次拣货: {}", waveId);
        warehouseService.startWavePicking(waveId);
        return Result.success();
    }

    /**
     * 确认拣货明细
     */
    @PutMapping("/waves/details/{detailId}/pick")
    public Result<Void> confirmPick(@PathVariable Long detailId,
                                    @RequestParam Integer pickedQuantity,
                                    @RequestParam String picker) {
        log.info("确认拣货: 明细={}, 数量={}, 拣货员={}", detailId, pickedQuantity, picker);
        warehouseService.confirmPick(detailId, pickedQuantity, picker);
        return Result.success();
    }

    /**
     * 完成波次
     */
    @PostMapping("/waves/{waveId}/complete")
    public Result<Void> completeWavePicking(@PathVariable Long waveId) {
        log.info("完成波次: {}", waveId);
        warehouseService.completeWavePicking(waveId);
        return Result.success();
    }

    /**
     * 取消波次
     */
    @PostMapping("/waves/{waveId}/cancel")
    public Result<Void> cancelWavePicking(@PathVariable Long waveId,
                                          @RequestParam(required = false) String reason) {
        log.info("取消波次: 波次={}, 原因={}", waveId, reason);
        warehouseService.cancelWavePicking(waveId, reason);
        return Result.success();
    }

    // ==================== 包装材料管理 ====================

    /**
     * 分页查询包装材料
     */
    @GetMapping("/materials/page")
    public Result<Page<PackingMaterial>> listPackingMaterials(@RequestParam(required = false) String materialType,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "20") Integer size) {
        log.info("分页查询包装材料: 类型={}", materialType);
        return Result.success(warehouseService.listPackingMaterials(materialType, page, size));
    }

    /**
     * 创建包装材料
     */
    @PostMapping("/materials")
    public Result<Long> createPackingMaterial(@RequestBody PackingMaterial material) {
        // Service 的出入库逻辑会直接累加 stockQuantity，这里兜底避免空值拆箱
        if (material.getStockQuantity() == null) {
            material.setStockQuantity(0);
        }
        log.info("创建包装材料请求: {}", material.getMaterialName());
        Long materialId = warehouseService.createPackingMaterial(material);
        return Result.success(materialId);
    }

    /**
     * 更新包装材料
     */
    @PutMapping("/materials/{materialId}")
    public Result<Void> updatePackingMaterial(@PathVariable Long materialId,
                                              @RequestBody PackingMaterial material) {
        material.setId(materialId);
        log.info("更新包装材料: {}", materialId);
        warehouseService.updatePackingMaterial(material);
        return Result.success();
    }

    /**
     * 删除包装材料
     */
    @DeleteMapping("/materials/{materialId}")
    public Result<Void> deletePackingMaterial(@PathVariable Long materialId) {
        log.info("删除包装材料: {}", materialId);
        warehouseService.deletePackingMaterial(materialId);
        return Result.success();
    }

    /**
     * 包装材料入库
     */
    @PostMapping("/materials/{materialId}/inbound")
    public Result<Void> inboundPackingMaterial(@PathVariable Long materialId,
                                               @RequestParam Integer quantity,
                                               @RequestParam(required = false) String remark) {
        log.info("包装材料入库: 材料={}, 数量={}, 备注={}", materialId, quantity, remark);
        warehouseService.inboundPackingMaterial(materialId, quantity, remark);
        return Result.success();
    }

    /**
     * 包装材料出库
     */
    @PostMapping("/materials/{materialId}/outbound")
    public Result<Void> outboundPackingMaterial(@PathVariable Long materialId,
                                                @RequestParam Integer quantity,
                                                @RequestParam(required = false) String remark) {
        log.info("包装材料出库: 材料={}, 数量={}, 备注={}", materialId, quantity, remark);
        warehouseService.outboundPackingMaterial(materialId, quantity, remark);
        return Result.success();
    }

    /**
     * 查询需要补货的包装材料
     */
    @GetMapping("/materials/replenishment")
    public Result<List<PackingMaterial>> getMaterialsNeedingReplenishment() {
        return Result.success(warehouseService.getMaterialsNeedingReplenishment());
    }

    /**
     * 波次创建请求
     */
    @Data
    public static class WaveCreateRequest {
        private WaveCreateDTO wave;
        private List<WavePickingDetail> details;
    }
}
