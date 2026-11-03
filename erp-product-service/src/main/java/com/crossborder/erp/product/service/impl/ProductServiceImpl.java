package com.crossborder.erp.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.product.entity.Product;
import com.crossborder.erp.product.mapper.ProductMapper;
import com.crossborder.erp.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    @Override
    public Product saveProduct(Product product) {
        // 🔴 必填校验：`t_product.internal_sku` / `product_name` 是 NOT NULL 且**无默认值**，
        //    MyBatis-Plus 的 insert 默认 FieldStrategy.NOT_NULL 会**跳过 null 字段**
        //    → INSERT 里根本没有这两列 → MySQL 报
        //    "Field 'internal_sku' doesn't have a default value" → 整个插入失败，
        //    而且错误信息是**原始 SQL 栈**直接泄露给前端。
        //    📌 注意实体字段名是 sku / name，列名是 internal_sku / product_name。
        if (product == null) {
            throw new BizException("商品不能为空");
        }
        if (product.getId() == null) {
            // 📌 只在**新增**时校验（更新走 updateById，MyBatis-Plus 只更新非 null 字段）
            if (product.getSku() == null || product.getSku().trim().isEmpty()) {
                throw new BizException("SKU 不能为空");
            }
            if (product.getName() == null || product.getName().trim().isEmpty()) {
                throw new BizException("商品名称不能为空");
            }
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
        return product;
    }

    @Override
    public Product getProductById(Long id) {
        return productMapper.selectById(id);
    }

    @Override
    public Product getProductBySku(String sku) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getSku, sku);
        return productMapper.selectOne(wrapper);
    }

    @Override
    public IPage<Product> pageProducts(Page<Product> page, String keyword, String brand) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Product::getName, keyword)
                   .or()
                   .like(Product::getSku, keyword)
                   .or()
                   .like(Product::getDescription, keyword);
        }
        
        if (brand != null && !brand.isEmpty()) {
            wrapper.eq(Product::getBrand, brand);
        }
        
        wrapper.orderByDesc(Product::getCreateTime);
        return productMapper.selectPage(page, wrapper);
    }

    @Override
    public void deleteProduct(Long id) {
        productMapper.deleteById(id);
    }

    @Override
    public int batchUpdateStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        if (status == null) {
            throw new BizException("目标状态不能为空");
        }
        // 只更新存在的商品，避免「传一堆不存在的 ID 也算成功」
        Product update = new Product();
        update.setStatus(status);
        int updated = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            Product exists = productMapper.selectById(id);
            if (exists == null) {
                log.warn("批量改状态跳过不存在的商品: id={}", id);
                continue;
            }
            updated += productMapper.updateById(update);
        }
        log.info("批量修改商品状态: status={}, 请求={} 条, 实际={} 条", status, ids.size(), updated);
        return updated;
    }
}