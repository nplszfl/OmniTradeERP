package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.client.TikTokApiClient;
import com.omnitrade.erp.social.dto.TikTokProductDTO;
import com.omnitrade.erp.social.model.LiveStream;
import com.omnitrade.erp.social.model.TikTokProduct;
import com.omnitrade.erp.social.model.TikTokShopConfig;
import com.omnitrade.erp.social.repository.LiveStreamRepository;
import com.omnitrade.erp.social.repository.TikTokProductRepository;
import com.omnitrade.erp.social.repository.TikTokShopConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 直播商品挂载服务
 * 负责在直播中挂载和移除商品
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveProductService {
    
    private final LiveStreamRepository liveStreamRepository;
    private final TikTokProductRepository productRepository;
    private final TikTokShopConfigRepository shopConfigRepository;
    private final TikTokApiClient apiClient;
    
    /**
     * 获取直播中可用的商品列表
     */
    public List<TikTokProductDTO> getAvailableProductsForLive(Long liveStreamId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new RuntimeException("直播不存在: " + liveStreamId));
        
        List<TikTokProduct> products = productRepository.findAvailableForLive(liveStream.getPlatform().name());
        
        return products.stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取直播中已挂载的商品列表
     */
    public List<TikTokProductDTO> getMountedProducts(Long liveStreamId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new RuntimeException("直播不存在: " + liveStreamId));
        
        // 从直播记录中获取已挂载的商品ID列表
        String productsJson = liveStream.getProducts();
        if (productsJson == null || productsJson.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            // 解析已挂载的商品ID列表
            List<String> productIds = parseProductIds(productsJson);
            List<TikTokProduct> products = productRepository.findAllById(
                    productRepository.findAll().stream()
                            .filter(p -> productIds.contains(p.getProductId()))
                            .map(TikTokProduct::getId)
                            .collect(Collectors.toList()));
            
            return products.stream()
                    .map(this::toProductDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("解析已挂载商品失败, liveStreamId: {}", liveStreamId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 挂载商品到直播
     */
    @Transactional
    public TikTokProductDTO mountProduct(Long liveStreamId, String productId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new RuntimeException("直播不存在: " + liveStreamId));
        
        if (liveStream.getStatus() != LiveStream.StreamStatus.LIVE) {
            throw new BizException("直播不在进行中，无法挂载商品");
        }
        
        TikTokProduct product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在: " + productId));
        
        // 通过API挂载商品
        TikTokShopConfig shop = shopConfigRepository.findByShopId(product.getShopId())
                .orElseThrow(() -> new RuntimeException("店铺配置不存在"));
        
        if (isTokenExpired(shop)) {
            refreshToken(shop);
        }
        
        try {
            TikTokApiClient.TikTokLiveProductResponse response = apiClient.addProductToLive(
                    shop.getAccessToken(), 
                    shop.getShopId(), 
                    liveStream.getStreamId(), 
                    productId);
            
            if (response.getData() == null || response.getErrorCode() != null) {
                throw new RuntimeException("挂载商品失败: " + response.getErrorMessage());
            }
            
            // 更新本地记录的已挂载商品列表
            updateMountedProducts(liveStream, productId, true);
            
            log.info("商品挂载成功, liveStreamId: {}, productId: {}", liveStreamId, productId);
            return toProductDTO(product);
            
        } catch (Exception e) {
            log.error("挂载商品到直播失败, liveStreamId: {}, productId: {}", liveStreamId, productId, e);
            throw new RuntimeException("挂载商品失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量挂载商品到直播
     */
    @Transactional
    public List<TikTokProductDTO> mountProducts(Long liveStreamId, List<String> productIds) {
        List<TikTokProductDTO> mountedProducts = new ArrayList<>();
        
        for (String productId : productIds) {
            try {
                TikTokProductDTO product = mountProduct(liveStreamId, productId);
                mountedProducts.add(product);
            } catch (Exception e) {
                log.error("挂载商品失败, productId: {}", productId, e);
                // 继续挂载其他商品
            }
        }
        
        return mountedProducts;
    }
    
    /**
     * 从直播移除商品
     */
    @Transactional
    public void unmountProduct(Long liveStreamId, String productId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new RuntimeException("直播不存在: " + liveStreamId));
        
        TikTokProduct product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在: " + productId));
        
        // 通过API移除商品
        TikTokShopConfig shop = shopConfigRepository.findByShopId(product.getShopId())
                .orElseThrow(() -> new RuntimeException("店铺配置不存在"));
        
        if (isTokenExpired(shop)) {
            refreshToken(shop);
        }
        
        try {
            TikTokApiClient.TikTokLiveProductResponse response = apiClient.removeProductFromLive(
                    shop.getAccessToken(), 
                    shop.getShopId(), 
                    liveStream.getStreamId(), 
                    productId);
            
            if (response.getData() == null || response.getErrorCode() != null) {
                throw new RuntimeException("移除商品失败: " + response.getErrorMessage());
            }
            
            // 更新本地记录
            updateMountedProducts(liveStream, productId, false);
            
            log.info("商品移除成功, liveStreamId: {}, productId: {}", liveStreamId, productId);
            
        } catch (Exception e) {
            log.error("从直播移除商品失败, liveStreamId: {}, productId: {}", liveStreamId, productId, e);
            throw new RuntimeException("移除商品失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 推荐适合直播的商品（基于历史销售数据）
     */
    public List<TikTokProductDTO> recommendProductsForLive(String platform, int limit) {
        List<TikTokProduct> allProducts = productRepository.findAvailableForLive(platform);
        
        // 按销量和评分排序，优先推荐热销商品
        List<TikTokProduct> sortedProducts = allProducts.stream()
                .sorted((p1, p2) -> {
                    int sales1 = p1.getSalesCount() != null ? p1.getSalesCount() : 0;
                    int sales2 = p2.getSalesCount() != null ? p2.getSalesCount() : 0;
                    return Integer.compare(sales2, sales1);
                })
                .limit(limit)
                .collect(Collectors.toList());
        
        return sortedProducts.stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据商品分类推荐
     */
    public List<TikTokProductDTO> recommendProductsByCategory(String platform, String category, int limit) {
        List<TikTokProduct> products = productRepository.findAvailableForLive(platform).stream()
                .filter(p -> p.getCategoryName() != null && p.getCategoryName().contains(category))
                .sorted((p1, p2) -> {
                    int sales1 = p1.getSalesCount() != null ? p1.getSalesCount() : 0;
                    int sales2 = p2.getSalesCount() != null ? p2.getSalesCount() : 0;
                    return Integer.compare(sales2, sales1);
                })
                .limit(limit)
                .collect(Collectors.toList());
        
        return products.stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 预设直播商品组合
     */
    public List<TikTokProductDTO> getProductBundle(String platform, String bundleType) {
        List<TikTokProduct> products;
        
        switch (bundleType) {
            case "HIGH_COMMISSION":
                // 高佣金商品
                products = productRepository.findAvailableForLive(platform).stream()
                        .filter(p -> p.getLiveCommissionRate() != null && p.getLiveCommissionRate().compareTo(java.math.BigDecimal.valueOf(20)) > 0)
                        .sorted((p1, p2) -> p2.getLiveCommissionRate().compareTo(p1.getLiveCommissionRate()))
                        .limit(10)
                        .collect(Collectors.toList());
                break;
            case "LOW_PRICE":
                // 低价引流商品
                products = productRepository.findAvailableForLive(platform).stream()
                        .filter(p -> p.getPrice() != null && p.getPrice().compareTo(java.math.BigDecimal.valueOf(20)) < 0)
                        .sorted(java.util.Comparator.comparing(TikTokProduct::getPrice))
                        .limit(10)
                        .collect(Collectors.toList());
                break;
            case "HOT_SELLING":
                // 热销商品
                products = productRepository.findAvailableForLive(platform).stream()
                        .sorted((p1, p2) -> {
                            int sales1 = p1.getSalesCount() != null ? p1.getSalesCount() : 0;
                            int sales2 = p2.getSalesCount() != null ? p2.getSalesCount() : 0;
                            return Integer.compare(sales2, sales1);
                        })
                        .limit(10)
                        .collect(Collectors.toList());
                break;
            default:
                products = productRepository.findAvailableForLive(platform).stream()
                        .limit(10)
                        .collect(Collectors.toList());
        }
        
        return products.stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());
    }
    
    // ========== 私有方法 ==========
    
    private void updateMountedProducts(LiveStream liveStream, String productId, boolean add) {
        String currentProducts = liveStream.getProducts();
        Set<String> productIds = new HashSet<>();
        
        if (currentProducts != null && !currentProducts.isEmpty()) {
            try {
                List<String> parsed = parseProductIds(currentProducts);
                productIds.addAll(parsed);
            } catch (Exception e) {
                log.error("解析已挂载商品失败", e);
            }
        }
        
        if (add) {
            productIds.add(productId);
        } else {
            productIds.remove(productId);
        }
        
        liveStream.setProducts(String.join(",", productIds));
        liveStreamRepository.save(liveStream);
    }
    
    private List<String> parseProductIds(String productsJson) {
        if (productsJson == null || productsJson.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(productsJson.split(","));
    }
    
    private boolean isTokenExpired(TikTokShopConfig shop) {
        if (shop.getTokenExpireTime() == null) {
            return true;
        }
        return shop.getTokenExpireTime().isBefore(java.time.LocalDateTime.now().plusHours(1));
    }
    
    private void refreshToken(TikTokShopConfig shop) {
        if (shop.getRefreshToken() == null) {
            throw new BizException("Refresh Token不存在，无法刷新");
        }
        
        try {
            TikTokApiClient.TokenResponse tokenResponse = apiClient.refreshAccessToken(shop.getRefreshToken());
            shop.setAccessToken(tokenResponse.getAccessToken());
            shop.setRefreshToken(tokenResponse.getRefreshToken());
            shop.setTokenExpireTime(java.time.LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
            shopConfigRepository.save(shop);
            log.info("Token刷新成功, shopId: {}", shop.getShopId());
        } catch (Exception e) {
            log.error("Token刷新失败, shopId: {}", shop.getShopId(), e);
            shop.setStatus(TikTokShopConfig.ConfigStatus.ERROR);
            shopConfigRepository.save(shop);
            throw new RuntimeException("Token刷新失败: " + e.getMessage(), e);
        }
    }
    
    private TikTokProductDTO toProductDTO(TikTokProduct entity) {
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
                .isLiveProduct(entity.getIsLiveProduct())
                .liveCommissionRate(entity.getLiveCommissionRate())
                .salesCount(entity.getSalesCount())
                .rating(entity.getRating())
                .localProductId(entity.getLocalProductId())
                .externalProductId(entity.getExternalProductId())
                .build();
    }
}