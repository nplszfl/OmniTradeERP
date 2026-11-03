package com.crossborder.erp.product.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.product.entity.Product;
import com.crossborder.erp.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 🔴 <b>NOT NULL 无默认值 → 插入必失败的回归测试</b>（product 模块）。
 *
 * <p><b>缺陷本体</b>：{@code t_product.internal_sku} / {@code product_name}
 * 都是 {@code NOT NULL} 且<b>无默认值</b>，
 * 而 MyBatis-Plus 的 insert 默认 {@code FieldStrategy.NOT_NULL}
 * 会<b>跳过 null 字段</b> → INSERT 语句里根本没有这两列 →
 * MySQL 报 {@code Field 'internal_sku' doesn't have a default value}
 * → <b>整个插入失败</b>，错误信息还是<b>原始 SQL 栈</b>直接泄露给前端。</p>
 *
 * <p>📌 注意实体字段名是 {@code sku} / {@code name}，列名是
 * {@code internal_sku} / {@code product_name} —— 这种错位很容易让巡检脚本漏判。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品模块：NOT NULL 无默认值列的必填校验")
class ProductRequiredFieldTest {

    @Mock
    private ProductMapper productMapper;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productMapper);
    }

    @Test
    @DisplayName("🔴 SKU 为空 → 报业务异常（不是 SQL 500）")
    void skuRequired() {
        Product product = new Product();
        product.setName("智能手表 X1");

        assertThatThrownBy(() -> productService.saveProduct(product))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("SKU 不能为空");

        verify(productMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 商品名为空 → 报业务异常")
    void nameRequired() {
        Product product = new Product();
        product.setSku("SKU-001");

        assertThatThrownBy(() -> productService.saveProduct(product))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品名称不能为空");

        verify(productMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 商品为 null → 报业务异常（不是 NPE）")
    void productNullHandled() {
        assertThatThrownBy(() -> productService.saveProduct(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品不能为空");

        verify(productMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ SKU + 名称齐全 → 正常插入")
    void validProductInserts() {
        Product product = new Product();
        product.setSku("SKU-001");
        product.setName("智能手表 X1");

        productService.saveProduct(product);

        verify(productMapper).insert(any(Product.class));
    }

    @Test
    @DisplayName("✅ 更新（id 非空）→ 走 update，不校验必填")
    void updatePathUnaffected() {
        Product product = new Product();
        product.setId(5001L);
        // 📌 更新路径只更新非 null 字段，不应该被新增校验拦住

        productService.saveProduct(product);

        verify(productMapper, never()).insert(any());
        verify(productMapper).updateById(any(Product.class));
    }
}
