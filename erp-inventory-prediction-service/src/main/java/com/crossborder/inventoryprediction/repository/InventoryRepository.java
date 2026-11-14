package com.crossborder.inventoryprediction.repository;

import com.crossborder.inventoryprediction.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 库存 Repository
 *
 * <p>📌 **2026-11-14 新增**：原先库存量是 {@code productId * 50 + random.nextInt(100)}。</p>
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** 某产品的所有仓库库存记录。 */
    List<Inventory> findByProductId(Long productId);

    /** 某产品在某仓库的库存（唯一键 uk_product_warehouse）。 */
    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
}
