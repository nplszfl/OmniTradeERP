package com.crossborder.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.purchase.mapper.PurchaseForecastMapper;
import com.crossborder.erp.purchase.service.PurchaseForecastService;
import com.crossborder.erp.purchase.entity.PurchaseForecast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购预测服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseForecastServiceImpl extends ServiceImpl<PurchaseForecastMapper, PurchaseForecast> implements PurchaseForecastService {

    @Override
    public PurchaseForecast predictPurchaseQuantity(Long productId, String sku, String warehouseId, int days) {
        log.info("预测采购量: productId={}, sku={}, warehouseId={}, days={}", productId, sku, warehouseId, days);
        PurchaseForecast forecast = new PurchaseForecast();
        forecast.setProductId(productId);
        forecast.setSku(sku);
        forecast.setWarehouseId(warehouseId);
        forecast.setPredictedQuantity(100);
        forecast.setCurrentStock(50);
        forecast.setSafeStock(30);
        forecast.setForecastPeriod(days + "天");
        forecast.setForecastDate(LocalDateTime.now());
        forecast.setStatus("PREDICTED");
        baseMapper.insert(forecast);
        return forecast;
    }

    @Override
    public List<PurchaseForecast> batchPredict(List<Long> productIds, String warehouseId, int days) {
        log.info("批量预测: productIds={}, warehouseId={}, days={}", productIds.size(), warehouseId, days);
        return productIds.stream().map(id -> predictPurchaseQuantity(id, "SKU-" + id, warehouseId, days)).toList();
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<PurchaseForecast> queryForecasts(int page, int size, Long productId, String warehouseId) {
        LambdaQueryWrapper<PurchaseForecast> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(PurchaseForecast::getProductId, productId);
        }
        if (warehouseId != null) {
            wrapper.eq(PurchaseForecast::getWarehouseId, warehouseId);
        }
        wrapper.orderByDesc(PurchaseForecast::getForecastDate);
        return page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
    }
}