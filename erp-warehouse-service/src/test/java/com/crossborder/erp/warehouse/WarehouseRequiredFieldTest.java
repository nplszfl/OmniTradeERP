package com.crossborder.erp.warehouse;

import com.crossborder.erp.common.exception.BusinessException;
import com.crossborder.erp.warehouse.service.impl.WarehouseServiceImpl;
import com.crossborder.erp.warehouse.entity.*;
import com.crossborder.erp.warehouse.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 🔴 <b>NOT NULL 无默认值 → 插入必失败的回归测试</b>。
 *
 * <p><b>缺陷本体</b>：{@code wms_*} 系列表里有一批列是
 * {@code NOT NULL} 且<b>无默认值</b>（{@code name} / {@code warehouse_id} /
 * {@code location_code} / {@code product_id} / {@code from_warehouse_id} …），
 * 而 MyBatis-Plus 的 insert 默认 {@code FieldStrategy.NOT_NULL}
 * 会<b>跳过 null 字段</b> → INSERT 语句里根本没有这一列 →
 * MySQL 报 {@code Field 'xxx' doesn't have a default value}
 * → <b>整个插入失败</b>，而且错误信息是<b>原始 SQL 栈</b>直接泄露给前端。</p>
 *
 * <p>📌 这些方法都是 {@code @RequestBody} 直传实体，客户端漏传字段就是 500。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("仓库模块：NOT NULL 无默认值列的必填校验")
class WarehouseRequiredFieldTest {

    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private WarehouseLocationMapper locationMapper;
    @Mock
    private InboundOrderMapper inboundOrderMapper;
    @Mock
    private InboundDetailMapper inboundDetailMapper;
    @Mock
    private OutboundOrderMapper outboundOrderMapper;
    @Mock
    private OutboundDetailMapper outboundDetailMapper;
    @Mock
    private InventoryBatchMapper batchMapper;
    @Mock
    private StockTransferMapper transferMapper;
    @Mock
    private PackingMaterialMapper packingMaterialMapper;
    @Mock
    private WavePickingMapper waveMapper;
    @Mock
    private WavePickingDetailMapper waveDetailMapper;
    @Mock
    private InventoryCheckMapper inventoryCheckMapper;
    @Mock
    private InventoryCheckResultMapper inventoryCheckResultMapper;

    private WarehouseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WarehouseServiceImpl(
                warehouseMapper, locationMapper, batchMapper, packingMaterialMapper,
                inboundOrderMapper, inboundDetailMapper, outboundOrderMapper, outboundDetailMapper,
                transferMapper, waveMapper, waveDetailMapper,
                inventoryCheckMapper, inventoryCheckResultMapper);
    }

    // ─────────── 仓库 ───────────

    @Test
    @DisplayName("🔴 仓库名为空 → 报业务异常（不是 SQL 500）")
    void warehouseNameRequired() {
        Warehouse warehouse = new Warehouse();
        // 📌 刻意不设 name（wms_warehouse.name NOT NULL 无默认值）

        assertThatThrownBy(() -> service.createWarehouse(warehouse))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仓库名称不能为空");

        verify(warehouseMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ 仓库名正常 → 正常插入")
    void warehouseWithNameOk() {
        Warehouse warehouse = new Warehouse();
        warehouse.setName("汉堡仓");

        service.createWarehouse(warehouse);

        verify(warehouseMapper).insert(any(Warehouse.class));
    }

    // ─────────── 库位 ───────────

    @Test
    @DisplayName("🔴 库位缺仓库/编码 → 报业务异常")
    void locationRequiredFields() {
        WarehouseLocation noWarehouse = new WarehouseLocation();
        noWarehouse.setLocationCode("A-01-01-01");
        assertThatThrownBy(() -> service.createLocation(noWarehouse))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所属仓库不能为空");

        WarehouseLocation noCode = new WarehouseLocation();
        noCode.setWarehouseId(1L);
        assertThatThrownBy(() -> service.createLocation(noCode))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库位编码不能为空");

        verify(locationMapper, never()).insert(any());
    }

    // ─────────── 入库 / 出库 ───────────

    @Test
    @DisplayName("🔴 入库单缺仓库 → 报业务异常")
    void inboundOrderWarehouseRequired() {
        InboundOrder order = new InboundOrder();
        assertThatThrownBy(() -> service.createInboundOrder(order, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入库仓库不能为空");

        verify(inboundOrderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 入库明细缺商品 → 报业务异常")
    void inboundDetailProductRequired() {
        InboundOrder order = new InboundOrder();
        order.setWarehouseId(1L);
        InboundDetail detail = new InboundDetail();
        // 📌 刻意不设 productId（wms_inbound_detail.product_id NOT NULL 无默认值）

        assertThatThrownBy(() -> service.createInboundOrder(order, List.of(detail)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入库明细的商品不能为空");

        verify(inboundOrderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 出库单缺仓库 → 报业务异常")
    void outboundOrderWarehouseRequired() {
        OutboundOrder order = new OutboundOrder();
        assertThatThrownBy(() -> service.createOutboundOrder(order, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出库仓库不能为空");

        verify(outboundOrderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 出库明细缺商品 → 报业务异常")
    void outboundDetailProductRequired() {
        OutboundOrder order = new OutboundOrder();
        order.setWarehouseId(1L);
        OutboundDetail detail = new OutboundDetail();

        assertThatThrownBy(() -> service.createOutboundOrder(order, List.of(detail)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出库明细的商品不能为空");

        verify(outboundOrderMapper, never()).insert(any());
    }

    // ─────────── 批次 / 调拨 / 包装材料 ───────────

    @Test
    @DisplayName("🔴 库存批次缺商品/仓库 → 报业务异常")
    void inventoryBatchRequiredFields() {
        InventoryBatch noProduct = new InventoryBatch();
        noProduct.setWarehouseId(1L);
        assertThatThrownBy(() -> service.createInventoryBatch(noProduct))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存批次的商品不能为空");

        InventoryBatch noWarehouse = new InventoryBatch();
        noWarehouse.setProductId(100L);
        assertThatThrownBy(() -> service.createInventoryBatch(noWarehouse))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存批次的仓库不能为空");

        verify(batchMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 调拨单缺仓库 / 调出=调入 → 报业务异常")
    void transferRequiredFields() {
        StockTransfer noFrom = new StockTransfer();
        noFrom.setToWarehouseId(2L);
        assertThatThrownBy(() -> service.createTransfer(noFrom))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调出仓库不能为空");

        StockTransfer same = new StockTransfer();
        same.setFromWarehouseId(1L);
        same.setToWarehouseId(1L);
        assertThatThrownBy(() -> service.createTransfer(same))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能相同");

        verify(transferMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 包装材料名称为空 → 报业务异常")
    void packingMaterialNameRequired() {
        PackingMaterial material = new PackingMaterial();
        assertThatThrownBy(() -> service.createPackingMaterial(material))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("包装材料名称不能为空");

        verify(packingMaterialMapper, never()).insert(any());
    }

    // ─────────── 波次 ───────────

    @Test
    @DisplayName("🔴 波次明细缺商品 → 报业务异常")
    void waveDetailProductRequired() {
        WavePicking wave = new WavePicking();
        WavePickingDetail detail = new WavePickingDetail();
        // 📌 刻意不设 productId（wms_wave_picking_detail.product_id NOT NULL 无默认值）

        assertThatThrownBy(() -> service.createWavePicking(wave, List.of(detail)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("波次明细的商品不能为空");

        verify(waveMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ 空明细列表仍然允许（不拦空，只拦 null）")
    void emptyDetailsStillAllowed() {
        InboundOrder order = new InboundOrder();
        order.setWarehouseId(1L);

        assertThatCode(() -> service.createInboundOrder(order, List.of()))
                .as("空明细是允许的 —— 拦掉会打断平台同步")
                .doesNotThrowAnyException();
    }
}
