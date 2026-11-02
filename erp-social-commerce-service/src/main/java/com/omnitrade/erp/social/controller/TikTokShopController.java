package com.omnitrade.erp.social.controller;

import com.omnitrade.erp.social.dto.TikTokShopConfigDTO;
import com.omnitrade.erp.social.service.TikTokShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tiktok-shop")
@RequiredArgsConstructor
@Tag(name = "TikTok店铺管理", description = "TikTok Shop店铺配置和API集成")
public class TikTokShopController {
    
    private final TikTokShopService service;
    
    @GetMapping
    @Operation(summary = "获取所有TikTok店铺")
    public ResponseEntity<List<TikTokShopConfigDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取TikTok店铺详情")
    public ResponseEntity<TikTokShopConfigDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }
    
    @GetMapping("/shop/{shopId}")
    @Operation(summary = "根据店铺ID获取")
    public ResponseEntity<TikTokShopConfigDTO> findByShopId(@PathVariable String shopId) {
        return ResponseEntity.ok(service.findByShopId(shopId));
    }
    
    @PostMapping
    @Operation(summary = "创建TikTok店铺配置")
    public ResponseEntity<TikTokShopConfigDTO> create(@Valid @RequestBody TikTokShopConfigDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新TikTok店铺配置")
    public ResponseEntity<TikTokShopConfigDTO> update(@PathVariable Long id, @RequestBody TikTokShopConfigDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除TikTok店铺配置")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/refresh-token")
    @Operation(summary = "刷新TikTok访问令牌")
    public ResponseEntity<TikTokShopConfigDTO> refreshToken(@PathVariable Long id) {
        return ResponseEntity.ok(service.refreshToken(id));
    }

    /**
     * 同步全部已配置的 TikTok 店铺（前端：socialApi.tiktokShop.sync → POST /api/v1/tiktok-shop/sync）
     *
     * <p>⚠️ 之前前端调的是 /sync，后端只有 /{id}/refresh-token（带 id）和 POST /（创建），
     * 路径对不上 → 一调就 404。</p>
     */
    @PostMapping("/sync")
    @Operation(summary = "同步全部TikTok店铺")
    public ResponseEntity<Map<String, Object>> syncAll() {
        List<TikTokShopConfigDTO> shops = service.findAll();
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (TikTokShopConfigDTO shop : shops) {
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