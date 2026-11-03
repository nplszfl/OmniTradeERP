package com.crossborder.erp.supplier.service;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.supplier.entity.PurchaseOrderItem;
import com.crossborder.erp.supplier.mapper.PurchaseOrderItemMapper;
import com.crossborder.erp.supplier.mapper.PurchaseOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 🔴 <b>NOT NULL 无默认值 → 插入必失败的回归测试</b>（采购明细）。
 *
 * <p><b>缺陷本体</b>：{@code t_purchase_order_item.sku} / {@code quantity}
 * 都是 {@code NOT NULL} 且<b>无默认值</b>，而 MyBatis-Plus 的 insert 默认
 * {@code FieldStrategy.NOT_NULL} 会<b>跳过 null 字段</b> → INSERT 语句里根本没有这一列 →
 * MySQL 报 {@code Field 'sku' doesn't have a default value}
 * → <b>整个插入失败</b>，错误信息还是<b>原始 SQL 栈</b>直接泄露给前端。</p>
 *
 * <p><b>两个写入路径都要盖住</b>：
 * <ol>
 *   <li>{@code POST /purchase/orders}（RestController）—— DTO → 实体的装配路径</li>
 *   <li>{@code POST /purchase/orders/{id}/item}（Controller）——
 *       <b>直接把实体当 @RequestBody 绑定</b>，连 setter 都不走 → Controller 层没法逐字段校验
 *       → 所以守卫必须放在<b>服务层</b>，才能盖住所有调用方</li>
 * </ol>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("采购明细：NOT NULL 无默认值列的必填校验")
class PurchaseOrderItemRequiredFieldTest {

    @Mock
    private PurchaseOrderItemMapper itemMapper;
    @Mock
    private PurchaseOrderMapper orderMapper;
    @Mock
    private SupplierService supplierService;

    private PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(itemMapper, supplierService);
        // 📌 addOrderItem 末尾会 recalculateOrderTotal → getById() → baseMapper
        //    MyBatis-Plus 的 ServiceImpl.baseMapper 是父类字段，要用反射塞进去
        ReflectionTestUtils.setField(service, "baseMapper", orderMapper);
    }

    // ─────────── 服务层：addOrderItem ───────────

    @Test
    @DisplayName("sku 为 null → 抛中文业务异常，且不执行 insert")
    void addOrderItem_nullSku_throws() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setOrderId(9001L);
        item.setQuantity(1);

        assertThatThrownBy(() -> service.addOrderItem(item))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("SKU");

        verify(itemMapper, never()).insert(any(PurchaseOrderItem.class));
    }

    @Test
    @DisplayName("sku 为空白串 → 同样抛异常（空串也是无效值）")
    void addOrderItem_blankSku_throws() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setOrderId(9001L);
        item.setSku("   ");
        item.setQuantity(1);

        assertThatThrownBy(() -> service.addOrderItem(item))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("SKU");

        verify(itemMapper, never()).insert(any(PurchaseOrderItem.class));
    }

    @Test
    @DisplayName("quantity 为 null → 抛中文业务异常，且不执行 insert")
    void addOrderItem_nullQuantity_throws() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setOrderId(9001L);
        item.setSku("SKU-001");

        assertThatThrownBy(() -> service.addOrderItem(item))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数量");

        verify(itemMapper, never()).insert(any(PurchaseOrderItem.class));
    }

    @Test
    @DisplayName("quantity <= 0 → 抛异常（0 数量会让小计和总金额算错）")
    void addOrderItem_nonPositiveQuantity_throws() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setOrderId(9001L);
        item.setSku("SKU-001");
        item.setQuantity(0);

        assertThatThrownBy(() -> service.addOrderItem(item))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数量");

        verify(itemMapper, never()).insert(any(PurchaseOrderItem.class));
    }

    @Test
    @DisplayName("item 本身为 null → 抛异常（不 NPE）")
    void addOrderItem_nullItem_throws() {
        assertThatThrownBy(() -> service.addOrderItem(null))
                .isInstanceOf(BizException.class);

        verify(itemMapper, never()).insert(any(PurchaseOrderItem.class));
    }

    @Test
    @DisplayName("合法数据 → 正常 insert，且小计由后端计算")
    void addOrderItem_valid_insertsAndComputesSubtotal() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setOrderId(9001L);
        item.setSku("SKU-001");
        item.setQuantity(3);
        item.setUnitPrice(new BigDecimal("12.50"));

        service.addOrderItem(item);

        // 小计 = 12.50 × 3 = 37.50（不信前端传值）
        assertThat(item.getSubtotal()).isEqualByComparingTo(new BigDecimal("37.50"));
        verify(itemMapper).insert(item);
    }

    @Test
    @DisplayName("subtotal 已由调用方算好 → 保留调用方的值，不覆盖")
    void addOrderItem_existingSubtotal_preserved() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setOrderId(9001L);
        item.setSku("SKU-001");
        item.setQuantity(3);
        item.setUnitPrice(new BigDecimal("12.50"));
        item.setSubtotal(new BigDecimal("40.00"));

        service.addOrderItem(item);

        assertThat(item.getSubtotal()).isEqualByComparingTo(new BigDecimal("40.00"));
        verify(itemMapper).insert(item);
    }
}
