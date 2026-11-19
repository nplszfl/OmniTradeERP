package com.crossborder.inventoryprediction.repository;

import com.crossborder.inventoryprediction.entity.SalesPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 销量预测结果 Repository
 *
 * <p>📌 **2026-11-19 新增**（配合补写路径）。
 * 📌 表上有唯一键 {@code uk_product_date (product_id, prediction_date)}
 * → 同一产品同一天只能有一行 → 重跑必须**更新**而不是插入（否则唯一键冲突 / 重复行）。
 */
@Repository
public interface SalesPredictionRepository extends JpaRepository<SalesPrediction, Long> {

    /** 某产品某天的预测（唯一键 uk_product_date） */
    Optional<SalesPrediction> findByProductIdAndPredictionDate(Long productId, LocalDate predictionDate);

    /** 某产品的全部预测（按日期升序） */
    List<SalesPrediction> findByProductIdOrderByPredictionDateAsc(Long productId);

    /** 某天全部产品的预测 */
    List<SalesPrediction> findByPredictionDate(LocalDate predictionDate);
}
