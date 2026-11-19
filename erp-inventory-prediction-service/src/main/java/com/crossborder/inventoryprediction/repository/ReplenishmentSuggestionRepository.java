package com.crossborder.inventoryprediction.repository;

import com.crossborder.inventoryprediction.entity.ReplenishmentSuggestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 补货建议 Repository
 *
 * <p>📌 **2026-11-19 新增**（配合补写路径）。
 * 📌 建议表**没有**唯一键 → 同一产品可以有多条历史建议（审批留痕是有意义的）；
 * 但**待审批（PENDING）的同一产品只应有一条** → 用
 * {@code findByProductIdAndStatus} 找出来做更新，避免每轮任务把待办刷成一片。
 */
@Repository
public interface ReplenishmentSuggestionRepository extends JpaRepository<ReplenishmentSuggestionEntity, Long> {

    /** 某产品的待审批建议 */
    Optional<ReplenishmentSuggestionEntity> findByProductIdAndStatus(Long productId, String status);

    /** 某产品的全部建议（按创建时间倒序） */
    List<ReplenishmentSuggestionEntity> findByProductIdOrderByCreateTimeDesc(Long productId);

    /** 某状态的建议（如 PENDING） */
    List<ReplenishmentSuggestionEntity> findByStatusOrderByCreateTimeDesc(String status);
}
