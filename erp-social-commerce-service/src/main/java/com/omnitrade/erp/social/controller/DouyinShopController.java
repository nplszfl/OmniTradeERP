package com.omnitrade.erp.social.controller;

import com.omnitrade.erp.social.model.DouyinShopConfig;
import com.omnitrade.erp.social.service.DouyinShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1/douyin-shop")
@RequiredArgsConstructor
@Tag(name = "抖音店铺管理", description = "抖音店铺配置和API集成")
public class DouyinShopController {
    
    private final DouyinShopService service;
    
    @GetMapping
    @Operation(summary = "获取所有抖音店铺")
    public ResponseEntity<List<DouyinShopConfig>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取抖音店铺详情")
    public ResponseEntity<DouyinShopConfig> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }
    
    @GetMapping("/shop/{shopId}")
    @Operation(summary = "根据店铺ID获取")
    public ResponseEntity<DouyinShopConfig> findByShopId(@PathVariable String shopId) {
        return ResponseEntity.ok(service.findByShopId(shopId));
    }
    
    @PostMapping
    @Operation(summary = "创建抖音店铺配置")
    public ResponseEntity<DouyinShopConfig> create(@RequestBody DouyinShopConfig entity) {
        return ResponseEntity.ok(service.create(entity));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新抖音店铺配置")
    public ResponseEntity<DouyinShopConfig> update(@PathVariable Long id, @RequestBody DouyinShopConfig entity) {
        return ResponseEntity.ok(service.update(id, entity));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除抖音店铺配置")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/refresh-token")
    @Operation(summary = "刷新抖音访问令牌")
    public ResponseEntity<DouyinShopConfig> refreshToken(@PathVariable Long id) {
        return ResponseEntity.ok(service.refreshToken(id));
    }

    /**
     * 同步全部已配置的抖音店铺（前端：socialApi.douyinShop.sync → POST /api/v1/douyin-shop/sync）
     *
     * <p>⚠️ 之前前端调 /sync，后端只有 /{id}/refresh-token，路径对不上 → 404。</p>
     */
    @PostMapping("/sync")
    @Operation(summary = "同步全部抖音店铺")
    public ResponseEntity<Map<String, Object>> syncAll() {
        List<DouyinShopConfig> shops = service.findAll();
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (DouyinShopConfig shop : shops) {
            try {
                service.refreshToken(shop.getId());
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(shop.getShopId() + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", failed == 0);
        result.put("message", "同步完成: 成功 " + success + " 个, 失败 " + failed + " 个");
        result.put("total", shops.size());
        result.put("synced", success);
        result.put("failed", failed);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return ResponseEntity.ok(result);
    }
}