package com.crossborder.erp.product.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商品批量操作请求
 *
 * <p>前端：{@code request.post('/product/batch/on-shelf', { ids })}</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class ProductBatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 待操作的商品ID列表
     */
    private List<Long> ids;

    /**
     * 目标状态：1=上架，0=下架（前端商品列表用的是这组数字）
     */
    private Integer status;
}
