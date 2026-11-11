package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnitrade.erp.social.client.TikTokApiClient;
import com.omnitrade.erp.social.dto.TikTokProductDTO;
import com.omnitrade.erp.social.model.TikTokProduct;
import com.omnitrade.erp.social.model.TikTokShopConfig;
import com.omnitrade.erp.social.repository.TikTokProductRepository;
import com.omnitrade.erp.social.repository.TikTokShopConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TikTok 商品管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokProductService {
    
    private final TikTokProductRepository productRepository;
    private final TikTokShopConfigRepository shopConfigRepository;
    private final TikTokApiClient apiClient;
    private final ObjectMapper objectMapper;
    
    /**
     * 同步所有店铺的商品
     */
    public void syncAllProducts() {
        List<TikTokShopConfig> activeShops = shopConfigRepository.findByStatus(TikTokShopConfig.ConfigStatus.ACTIVE);
        for (TikTokShopConfig shop : activeShops) {
            try {
                syncProductsByShop(shop);
            } catch (Exception e) {
                log.error("同步店铺商品失败, shopId: {}", shop.getShopId(), e);
            }
        }
    }
    
    /**
     * 根据店铺同步商品
     */
    @Transactional(noRollbackFor = BizException.class)
    public void syncProductsByShop(TikTokShopConfig shop) {
        log.info("开始同步TikTok商品, shopId: {}", shop.getShopId());
        
        // 检查token是否过期
        if (isTokenExpired(shop)) {
            refreshToken(shop);
        }
        
        String accessToken = shop.getAccessToken();
        String shopId = shop.getShopId();
        
        String pageToken = null;
        int pageSize = 100;
        int totalSynced = 0;
        
        do {
            TikTokApiClient.TikTokProductListResponse response = apiClient.getProducts(
                    accessToken, shopId, pageToken, pageSize);
            
            if (response.getData() != null && response.getData().getProducts() != null) {
                for (Map<String, Object> productData : response.getData().getProducts()) {
                    try {
                        saveOrUpdateProduct(shopId, productData);
                        totalSynced++;
                    } catch (Exception e) {
                        log.error("保存商品失败: {}", productData.get("product_id"), e);
                    }
                }
                pageToken = response.getData().getPageToken();
            } else {
                pageToken = null;
            }
            
        } while (pageToken != null);
        
        log.info("商品同步完成, shopId: {}, 共同步: {} 条", shopId, totalSynced);
    }
    
    /**
     * 同步单个商品详情
     */
    @Transactional(noRollbackFor = BizException.class)
    public TikTokProductDTO syncProductDetail(Long shopConfigId, String productId) {
        TikTokShopConfig shop = shopConfigRepository.findById(shopConfigId)
                .orElseThrow(() -> new BizException("店铺配置不存在: " + shopConfigId));
        
        if (isTokenExpired(shop)) {
            refreshToken(shop);
        }
        
        TikTokApiClient.TikTokProductDetailResponse response = apiClient.getProductDetail(
                shop.getAccessToken(), shop.getShopId(), productId);
        
        if (response.getData() != null) {
            saveOrUpdateProduct(shop.getShopId(), response.getData());
            return findByProductId(productId);
        }
        
        throw new RuntimeException("商品同步失败: " + productId);
    }
    
    /**
     * 定时同步任务 - 每6小时执行一次
     */
    @Scheduled(fixedRate = 21600000)
    public void scheduledSync() {
        log.info("开始定时同步TikTok商品");
        try {
            syncAllProducts();
        } catch (Exception e) {
            log.error("定时同步商品失败", e);
        }
    }
    
    // ========== CRUD 操作 ==========
    
    public List<TikTokProductDTO> findAll() {
        return productRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public TikTokProductDTO findById(Long id) {
        return productRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new BizException("商品不存在: " + id));
    }
    
    public TikTokProductDTO findByProductId(String productId) {
        return productRepository.findByProductId(productId)
                .map(this::toDTO)
                .orElseThrow(() -> new BizException("商品不存在: " + productId));
    }
    
    public List<TikTokProductDTO> findByShopId(String shopId) {
        return productRepository.findByShopId(shopId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokProductDTO> findByStatus(String status) {
        return productRepository.findByStatus(TikTokProduct.ProductStatus.valueOf(status)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokProductDTO> findLiveProducts() {
        return productRepository.findLiveProducts().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokProductDTO> findAvailableForLive(String shopId) {
        return productRepository.findAvailableForLive(shopId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokProductDTO> searchByName(String keyword) {
        return productRepository.searchByName(keyword).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokProductDTO> searchByNameAndShopId(String shopId, String keyword) {
        return productRepository.searchByNameAndShopId(shopId, keyword).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokProductDTO> findLowStockProducts(String shopId, Integer threshold) {
        return productRepository.findLowStockProducts(shopId, threshold).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 创建商品（直播挂载用）
     */
    @Transactional
    public TikTokProductDTO createForLive(Long shopConfigId, TikTokProductDTO dto) {
        TikTokShopConfig shop = shopConfigRepository.findById(shopConfigId)
                .orElseThrow(() -> new BizException("店铺配置不存在: " + shopConfigId));
        
        TikTokProduct product = TikTokProduct.builder()
                .productId(dto.getProductId())
                .shopId(shop.getShopId())
                .productName(dto.getProductName())
                .description(dto.getDescription())
                .status(TikTokProduct.ProductStatus.ACTIVE)
                .price(dto.getPrice())
                .originalPrice(dto.getOriginalPrice())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
                .stockQuantity(dto.getStockQuantity())
                .mainImageUrl(dto.getMainImageUrl())
                .imageUrls(dto.getImageUrls())
                .isLiveProduct(dto.getIsLiveProduct() != null ? dto.getIsLiveProduct() : true)
                .liveCommissionRate(dto.getLiveCommissionRate())
                .localProductId(dto.getLocalProductId())
                .externalProductId(dto.getExternalProductId())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        TikTokProduct saved = productRepository.save(product);
        return toDTO(saved);
    }
    
    /**
     * 更新本地商品关联
     */
    @Transactional
    public TikTokProductDTO updateLocalProductId(String productId, String localProductId) {
        TikTokProduct product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BizException("商品不存在: " + productId));
        
        product.setLocalProductId(localProductId);
        return toDTO(productRepository.save(product));
    }
    
    /**
     * 标记为直播商品
     */
    @Transactional
    public TikTokProductDTO markAsLiveProduct(String productId, BigDecimal commissionRate) {
        TikTokProduct product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BizException("商品不存在: " + productId));
        
        product.setIsLiveProduct(true);
        product.setLiveCommissionRate(commissionRate);
        return toDTO(productRepository.save(product));
    }
    
    /**
     * 统计店铺商品
     */
    public Map<String, Object> getShopProductStats(String shopId) {
        long totalCount = productRepository.countByShopId(shopId);
        long activeCount = productRepository.countActiveByShopId(shopId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("activeCount", activeCount);
        
        return stats;
    }
    
    // ========== 私有方法 ==========
    
    private void saveOrUpdateProduct(String shopId, Map<String, Object> productData) {
        String productId = (String) productData.get("product_id");
        
        TikTokProduct product = productRepository.findByProductId(productId)
                .orElse(TikTokProduct.builder().build());
        
        product.setProductId(productId);
        product.setShopId(shopId);
        
        // 基本信息
        product.setProductName((String) productData.get("product_name"));
        product.setDescription((String) productData.get("description"));
        
        // 价格
        if (productData.get("price") != null) {
            product.setPrice(new BigDecimal(productData.get("price").toString()));
        }
        if (productData.get("original_price") != null) {
            product.setOriginalPrice(new BigDecimal(productData.get("original_price").toString()));
        }
        
        product.setCurrency((String) productData.get("currency"));
        
        // 库存
        if (productData.get("stock_quantity") != null) {
            product.setStockQuantity(Integer.parseInt(productData.get("stock_quantity").toString()));
        }
        
        // 状态
        String status = (String) productData.get("status");
        if (status != null) {
            product.setStatus(parseProductStatus(status));
        }
        
        // 图片
        if (productData.get("images") != null) {
            try {
                product.setImageUrls(objectMapper.writeValueAsString(productData.get("images")));
                // 取第一张作为主图
                List<String> images = objectMapper.readValue(
                        objectMapper.writeValueAsString(productData.get("images")),
                        new TypeReference<List<String>>() {});
                if (!images.isEmpty()) {
                    product.setMainImageUrl(images.get(0));
                }
            } catch (Exception e) {
                log.error("解析商品图片失败", e);
            }
        }
        
        // SKU
        if (productData.get("skus") != null) {
            try {
                product.setSkus(objectMapper.writeValueAsString(productData.get("skus")));
            } catch (Exception e) {
                log.error("序列化SKU失败", e);
            }
        }
        
        // 类目
        if (productData.get("category") != null) {
            Map<String, Object> category = (Map<String, Object>) productData.get("category");
            product.setCategoryId((String) category.get("category_id"));
            product.setCategoryName((String) category.get("category_name"));
        }
        
        // 统计数据
        if (productData.get("sales_count") != null) {
            product.setSalesCount(Integer.parseInt(productData.get("sales_count").toString()));
        }
        if (productData.get("rating") != null) {
            product.setRating(new BigDecimal(productData.get("rating").toString()));
        }
        
        product.setUpdateTime(LocalDateTime.now());
        
        productRepository.save(product);
    }
    
    private TikTokProduct.ProductStatus parseProductStatus(String status) {
        switch (status.toUpperCase()) {
            case "DRAFT": return TikTokProduct.ProductStatus.DRAFT;
            case "PENDING": return TikTokProduct.ProductStatus.PENDING;
            case "ACTIVE": return TikTokProduct.ProductStatus.ACTIVE;
            case "INACTIVE": return TikTokProduct.ProductStatus.INACTIVE;
            case "DELETED": return TikTokProduct.ProductStatus.DELETED;
            case "REJECTED": return TikTokProduct.ProductStatus.REJECTED;
            default: return TikTokProduct.ProductStatus.DRAFT;
        }
    }
    
    private boolean isTokenExpired(TikTokShopConfig shop) {
        if (shop.getTokenExpireTime() == null) {
            return true;
        }
        return shop.getTokenExpireTime().isBefore(LocalDateTime.now().plusHours(1));
    }
    
    private void refreshToken(TikTokShopConfig shop) {
        if (shop.getRefreshToken() == null) {
            throw new BizException("Refresh Token不存在，无法刷新");
        }
        
        try {
            TikTokApiClient.TokenResponse tokenResponse = apiClient.refreshAccessToken(shop.getRefreshToken());
            shop.setAccessToken(tokenResponse.getAccessToken());
            shop.setRefreshToken(tokenResponse.getRefreshToken());
            shop.setTokenExpireTime(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
            shopConfigRepository.save(shop);
            log.info("Token刷新成功, shopId: {}", shop.getShopId());
        } catch (Exception e) {
            log.error("Token刷新失败, shopId: {}", shop.getShopId(), e);
            // 📌 **标记状态也要走 DB，但它自己失败时不能盖掉原始异常**（否则根因丢失）
            try {
                shop.setStatus(TikTokShopConfig.ConfigStatus.ERROR);
                shopConfigRepository.save(shop);
            } catch (Exception dbEx) {
                log.error("标记店铺状态失败, shopId: {}", shop.getShopId(), dbEx);
            }
            // 📌 **刷新失败必须抛出去，不能吞掉**（2026-11-10 22:50 第 50 道闸门 / 兄弟分支）：
            //    之前 catch 里只 log + 标 ERROR 就**返回了** → 调用方**继续用那个过期 token 去调 API**
            //    → 必然 401 → 再被外层 catch 吞掉 → **每轮刷一条 error** → 真故障被噪音淹没（规则 43）。
            //    📌 **同一概念必须一个口径**（规则 44 / 107）：
            //    `LiveStreamMonitorService.refreshToken` / `LiveProductService.refreshToken` 都抛 →
            //    本方法必须**对齐**。
            throw new BizException("Token刷新失败: " + e.getMessage());
        }
    }
    
    private TikTokProductDTO toDTO(TikTokProduct entity) {
        return TikTokProductDTO.builder()
                .id(entity.getId())
                .productId(entity.getProductId())
                .shopId(entity.getShopId())
                .productName(entity.getProductName())
                .description(entity.getDescription())
                .categoryId(entity.getCategoryId())
                .categoryName(entity.getCategoryName())
                .status(entity.getStatus().name())
                .price(entity.getPrice())
                .originalPrice(entity.getOriginalPrice())
                .currency(entity.getCurrency())
                .stockQuantity(entity.getStockQuantity())
                .mainImageUrl(entity.getMainImageUrl())
                .imageUrls(entity.getImageUrls())
                .videoUrl(entity.getVideoUrl())
                .skus(entity.getSkus())
                .attributes(entity.getAttributes())
                .weight(entity.getWeight())
                .supportCod(entity.getSupportCod())
                .isLiveProduct(entity.getIsLiveProduct())
                .liveCommissionRate(entity.getLiveCommissionRate())
                .salesCount(entity.getSalesCount())
                .viewCount(entity.getViewCount())
                .favoriteCount(entity.getFavoriteCount())
                .rating(entity.getRating())
                .reviewCount(entity.getReviewCount())
                .localProductId(entity.getLocalProductId())
                .externalProductId(entity.getExternalProductId())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .publishTime(entity.getPublishTime())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
}