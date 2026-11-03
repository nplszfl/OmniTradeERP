package com.crossborder.aiassistant.repository;

import com.crossborder.aiassistant.entity.KnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库（问答对）仓储
 *
 * 📌 对应表 {@code knowledge_base}。
 *    之前这张表有种子数据但**没有任何代码读它**（服务用的是内存 ArrayList）。
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    /** 所有启用的条目（检索用） */
    List<KnowledgeBase> findByActiveTrue();

    /** 按分类查 */
    List<KnowledgeBase> findByCategoryAndActiveTrue(String category);

    /** 分页查（管理界面用） */
    Page<KnowledgeBase> findByActiveTrue(Pageable pageable);

    /** 按分类分页查（管理界面用） */
    Page<KnowledgeBase> findByCategoryAndActiveTrue(String category, Pageable pageable);

    /** 统计启用条目数 */
    long countByActiveTrue();
}
