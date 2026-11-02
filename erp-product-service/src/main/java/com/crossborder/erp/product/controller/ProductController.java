package com.crossborder.erp.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.product.entity.Product;
import com.crossborder.erp.product.service.ProductService;
import com.crossborder.erp.product.vo.ProductBatchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    /** 商品状态：1=上架，0=下架（与前端商品列表的数字状态一致） */
    private static final Integer PRODUCT_STATUS_ON_SHELF = 1;
    private static final Integer PRODUCT_STATUS_OFF_SHELF = 0;

    private final ProductService productService;

    @PostMapping
    public Result<Product> saveProduct(@RequestBody Product product) {
        Product saved = productService.saveProduct(product);
        return Result.success(saved);
    }

    /**
     * 修改商品（前端：productApi.updateProduct → {@code PUT /product}）
     *
     * <p>⚠️ 之前只有 POST /product，前端发的是 PUT → 405。</p>
     */
    @PutMapping
    public Result<Product> updateProduct(@RequestBody Product product) {
        if (product.getId() == null) {
            throw new BizException("商品ID不能为空");
        }
        Product exists = productService.getProductById(product.getId());
        if (exists == null) {
            throw new BizException("商品不存在: " + product.getId());
        }
        Product saved = productService.saveProduct(product);
        return Result.success(saved);
    }

    /**
     * 批量上架（前端：productApi.batchOnShelf → {@code POST /product/batch/on-shelf}）
     */
    @PostMapping("/batch/on-shelf")
    public Result<Integer> batchOnShelf(@RequestBody ProductBatchRequest request) {
        return Result.success(productService.batchUpdateStatus(request.getIds(), PRODUCT_STATUS_ON_SHELF));
    }

    /**
     * 批量下架（前端：productApi.batchOffShelf → {@code POST /product/batch/off-shelf}）
     */
    @PostMapping("/batch/off-shelf")
    public Result<Integer> batchOffShelf(@RequestBody ProductBatchRequest request) {
        return Result.success(productService.batchUpdateStatus(request.getIds(), PRODUCT_STATUS_OFF_SHELF));
    }

    @GetMapping("/{id}")
    public Result<Product> getProduct(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return Result.success(product);
    }

    @GetMapping("/sku/{sku}")
    public Result<Product> getProductBySku(@PathVariable String sku) {
        Product product = productService.getProductBySku(sku);
        return Result.success(product);
    }

    @GetMapping("/list")
    public Result<PageResult<Product>> listProducts(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String brand) {

        Page<Product> page = new Page<>(current, size);
        IPage<Product> result = productService.pageProducts(page, keyword, brand);
        // MyBatis-Plus count workaround: ensure total is accurate
        PageResult<Product> pageResult = PageResult.of(result);
        if (pageResult.getTotal() == 0 && !result.getRecords().isEmpty()) {
            pageResult.setTotal((long) result.getRecords().size());
        }
        return Result.success(pageResult);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }
}
