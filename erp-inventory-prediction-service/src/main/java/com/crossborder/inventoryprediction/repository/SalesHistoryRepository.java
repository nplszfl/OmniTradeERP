package com.crossborder.inventoryprediction.repository;

import com.crossborder.inventoryprediction.entity.SalesHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售历史 Repository
 *
 * <p>📌 **2026-11-14 新增**：这个模块原先**没有 Repository**，
 * 销量全靠 {@code Random} 现造。</p>
 */
@Repository
public interface SalesHistoryRepository extends JpaRepository<SalesHistory, Long> {

    /**
     * 取某产品最近 N 天的销售历史（按日期升序）。
     *
     * <p>📌 用 {@code @Param} 显式命名 —— 否则 Spring Data JPA 在
     * {@code -parameters} 编译参数没开时会报
     * {@code Parameter name not found}（规则：命名参数必须显式绑定）。</p>
     */
    @Query("SELECT s FROM SalesHistory s "
            + "WHERE s.productId = :productId AND s.salesDate >= :since "
            + "ORDER BY s.salesDate ASC")
    List<SalesHistory> findRecentSales(@Param("productId") Long productId,
                                       @Param("since") LocalDate since);

    /** 某产品某天的销售记录（唯一键 uk_product_date）。 */
    java.util.Optional<SalesHistory> findByProductIdAndSalesDate(Long productId, LocalDate salesDate);
}
