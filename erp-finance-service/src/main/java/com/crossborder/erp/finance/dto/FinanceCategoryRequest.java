package com.crossborder.erp.finance.dto;

import lombok.Data;

/**
 * 财务分类请求对象
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Data
public class FinanceCategoryRequest {

    /** 分类 ID（更新时必填） */
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

    /** 备注 */
    private String remark;
}
