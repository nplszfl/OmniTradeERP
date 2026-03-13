package com.crossborder.erp.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.platform.entity.PlatformConfig;
import com.crossborder.erp.platform.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台配置Controller
 */
@RestController
@RequestMapping("/platform/config")
@RequiredArgsConstructor
public class PlatformConfigController {

    private final PlatformConfigService platformConfigService;

    /**
     * 获取配置列表
     */
    @GetMapping("/list")
    public Result<List<PlatformConfig>> getConfigList() {
        List<PlatformConfig> configs = platformConfigService.getAllConfigs();
        return Result.success(configs);
    }

    /**
     * 获取单个配置
     */
    @GetMapping("/{id}")
    public Result<PlatformConfig> getConfig(@PathVariable Long id) {
        PlatformConfig config = platformConfigService.getConfigById(id);
        return Result.success(config);
    }

    /**
     * 保存配置
     */
    @PostMapping
    public Result<PlatformConfig> saveConfig(@RequestBody PlatformConfig config) {
        PlatformConfig saved = platformConfigService.saveConfig(config);
        return Result.success(saved);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        platformConfigService.deleteConfig(id);
        return Result.success();
    }

    /**
     * 测试连接
     */
    @PostMapping("/{id}/test")
    public Result<Boolean> testConnection(@PathVariable Long id) {
        boolean valid = platformConfigService.testConnection(id);
        return Result.success(valid);
    }

    /**
     * 启用/停用配置
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        platformConfigService.updateStatus(id, status);
        return Result.success();
    }
}
