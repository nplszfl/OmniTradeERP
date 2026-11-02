package com.crossborder.erp.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.product.entity.Product;

import java.util.List;

public interface ProductService {

    Product saveProduct(Product product);

    Product getProductById(Long id);

    Product getProductBySku(String sku);

    IPage<Product> pageProducts(Page<Product> page, String keyword, String brand);

    /**
     * 批量修改商品状态（上架/下架）
     *
     * @param ids    商品ID列表
     * @param status 目标状态：1=上架，0=下架
     * @return 实际更新条数
     */
    int batchUpdateStatus(List<Long> ids, Integer status);

    void deleteProduct(Long id);
}
