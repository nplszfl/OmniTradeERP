package com.crossborder.erp.finance.vo;

import lombok.Data;

/**
 * 财务分类视图对象
 *
 * <p>字段名与前端契约 {@code erp-web/src/api/finance.ts} 的 {@code FinanceCategory} 保持一致：
 * {@code sort} ↔ 实体 {@code sortOrder}，{@code status} ↔ 实体 {@code enabled}。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Data
public class FinanceCategoryVO {

    /** 分类 ID */
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类类型：income / expense */
    private String type;

    /** 父分类 ID */
    private Long parentId;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建时间 */
    private java.time.LocalDateTime createTime;
}
