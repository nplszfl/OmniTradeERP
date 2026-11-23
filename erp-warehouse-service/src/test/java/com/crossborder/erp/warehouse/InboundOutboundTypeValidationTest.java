package com.crossborder.erp.warehouse;

import com.crossborder.erp.common.exception.BusinessException;
import com.crossborder.erp.warehouse.entity.InboundDetail;
import com.crossborder.erp.warehouse.entity.InboundOrder;
import com.crossborder.erp.warehouse.entity.OutboundDetail;
import com.crossborder.erp.warehouse.entity.OutboundOrder;
import com.crossborder.erp.warehouse.entity.WarehouseLocation;
import com.crossborder.erp.warehouse.enums.InboundType;
import com.crossborder.erp.warehouse.enums.OutboundType;
import com.crossborder.erp.warehouse.mapper.InboundDetailMapper;
import com.crossborder.erp.warehouse.mapper.InboundOrderMapper;
import com.crossborder.erp.warehouse.mapper.InventoryBatchMapper;
import com.crossborder.erp.warehouse.mapper.InventoryCheckMapper;
import com.crossborder.erp.warehouse.mapper.InventoryCheckResultMapper;
import com.crossborder.erp.warehouse.mapper.OutboundDetailMapper;
import com.crossborder.erp.warehouse.mapper.OutboundOrderMapper;
import com.crossborder.erp.warehouse.mapper.PackingMaterialMapper;
import com.crossborder.erp.warehouse.mapper.StockTransferMapper;
import com.crossborder.erp.warehouse.mapper.WarehouseLocationMapper;
import com.crossborder.erp.warehouse.mapper.WarehouseMapper;
import com.crossborder.erp.warehouse.mapper.WavePickingDetailMapper;
import com.crossborder.erp.warehouse.mapper.WavePickingMapper;
import com.crossborder.erp.warehouse.service.impl.WarehouseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 🔴 <b>入/出库类型枚举没有任何校验 —— 脏数据直接进库</b>。
 *
 * <p><b>缺陷本体</b>：{@code wms_inbound_order.inbound_type} 与
 * {@code wms_outbound_order.outbound_type} 是 {@code VARCHAR(30)}，
 * <b>没有 ENUM 约束、没有 CHECK 约束、没有外键</b>。而
 * {@code InboundType} / {@code OutboundType} 两个枚举
 * （{@code PURCHASE/RETURN/TRANSFER/PRODUCTION/OTHER}、
 * {@code SALE/TRANSFER/RETURN/SAMPLE/DAMAGE/OTHER}）
 * 在 {@code WarehouseServiceImpl} 里<b>只被 import，从未被使用</b>
 * —— {@code createInboundOrder} / {@code createOutboundOrder}
 * 不校验类型合法性。</p>
 *
 * <p><b>为什么这是真缺陷而不是"洁癖"</b>：入库/出库类型直接决定
 * <b>库存怎么动</b>。{@code RETURN}（退货入库）必须让库存<b>增加</b>，
 * 而 {@code PURCHASE}（采购入库）走的是采购结算链路；
 * 一条 {@code inbound_type='RETRUN'}（拼错）落到表里，
 * 后续所有按类型分组的库存报表、对账、审计都<b>静默漏掉这条单据</b>，
 * 而且<b>没有任何报错</b>。</p>
 *
 * <p>对比：同项目的 {@code erp-finance-service} 在
 * {@code FinanceConverter} 里对 {@code FinanceType} / {@code CategoryType}
 * 都做了 {@code getByCode} 校验并抛业务异常 —— 仓库模块是<b>漏网的那一个</b>。</p>
 *
 * <p>📌 <b>本测试还钉住了两个容易漏掉的点</b>（写这个测试时真的差点漏）：
 * <ol>
 *   <li><b>归一化必须写回实体</b>：{@code getByCode} 是精确匹配，
 *       只校验不写回的话，{@code "purchase"} 会通过校验但以小写落库
 *       → 后续查不出来 → 报表照样漏行（"放行但没治好"）。</li>
 *   <li><b>空值必须真的落成 OTHER</b>：只"不报错"是不够的 ——
 *       库里留 NULL 的话，按类型分组的报表还是会漏这一行。</li>
 * </ol>
 *
 * @author 火球鼠
 * @since 2026-11-21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("仓库模块：入/出库类型必须是合法枚举值")
class InboundOutboundTypeValidationTest {

    @Mock private WarehouseMapper warehouseMapper;
    @Mock private WarehouseLocationMapper locationMapper;
    @Mock private InventoryBatchMapper batchMapper;
    @Mock private PackingMaterialMapper packingMaterialMapper;
    @Mock private InboundOrderMapper inboundOrderMapper;
    @Mock private InboundDetailMapper inboundDetailMapper;
    @Mock private OutboundOrderMapper outboundOrderMapper;
    @Mock private OutboundDetailMapper outboundDetailMapper;
    @Mock private StockTransferMapper transferMapper;
    @Mock private WavePickingMapper waveMapper;
    @Mock private WavePickingDetailMapper waveDetailMapper;
    @Mock private InventoryCheckMapper inventoryCheckMapper;
    @Mock private InventoryCheckResultMapper inventoryCheckResultMapper;

    private WarehouseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WarehouseServiceImpl(
                warehouseMapper, locationMapper, batchMapper, packingMaterialMapper,
                inboundOrderMapper, inboundDetailMapper, outboundOrderMapper, outboundDetailMapper,
                transferMapper, waveMapper, waveDetailMapper,
                inventoryCheckMapper, inventoryCheckResultMapper);

        // 📌 createOutboundOrder 在插单后会 lockInventory → 需要一个「有货」的库位，
        //    否则合法类型也会被后面的「库存不存在」打断，测不到类型校验本身。
        //    用 lenient()：入库路径不走这里，strict 模式会报 UnnecessaryStubbing。
        WarehouseLocation location = new WarehouseLocation();
        location.setId(1L);
        location.setStockQuantity(100);
        location.setLockedQuantity(0);
        lenient().when(locationMapper.selectOne(any())).thenReturn(location);
    }

    private InboundDetail inboundDetail() {
        InboundDetail detail = new InboundDetail();
        detail.setProductId(1L);
        detail.setQuantity(10);
        return detail;
    }

    private OutboundDetail outboundDetail() {
        OutboundDetail detail = new OutboundDetail();
        detail.setProductId(1L);
        detail.setQuantity(10);
        return detail;
    }

    @Nested
    @DisplayName("入库类型 inbound_type")
    class Inbound {

        @Test
        @DisplayName("🔴 类型值拼错 → 报业务异常，不落库")
        void inboundTypeTypoRejected() {
            InboundOrder order = new InboundOrder();
            order.setWarehouseId(1L);
            order.setInboundType("RETRUN");   // ← RETURN 的拼写错误

            assertThatThrownBy(() -> service.createInboundOrder(order, List.of(inboundDetail())))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("入库类型");

            verify(inboundOrderMapper, never()).insert(any());
        }

        @Test
        @DisplayName("✅ 类型为 null → 归到 OTHER（DDL 可空，平台同步建单不带类型）")
        void inboundTypeNullAllowed() {
            InboundOrder order = new InboundOrder();
            order.setWarehouseId(1L);
            order.setInboundType(null);

            assertThatCode(() -> service.createInboundOrder(order, List.of(inboundDetail())))
                    .doesNotThrowAnyException();

            // 📌 不能只是「不报错」—— 必须**真的写成 OTHER**，
            //    否则库里还是 NULL → 按类型分组的报表照样漏行。
            assertThat(order.getInboundType())
                    .as("null 类型必须落成 OTHER，不能是 null")
                    .isEqualTo(InboundType.OTHER.getCode());
            assertThat(InboundType.getByCode(order.getInboundType()))
                    .as("落库值必须能被 getByCode 查到")
                    .isNotNull();
        }

        @Test
        @DisplayName("✅ 类型为空白 → 归到 OTHER（同样按未指定处理）")
        void inboundTypeBlankAllowed() {
            InboundOrder order = new InboundOrder();
            order.setWarehouseId(1L);
            order.setInboundType("   ");

            assertThatCode(() -> service.createInboundOrder(order, List.of(inboundDetail())))
                    .doesNotThrowAnyException();

            assertThat(order.getInboundType())
                    .as("空白类型必须落成 OTHER")
                    .isEqualTo(InboundType.OTHER.getCode());
        }

        @Test
        @DisplayName("✅ 全部合法枚举值都放行")
        void allLegalInboundTypesAccepted() {
            for (InboundType type : InboundType.values()) {
                InboundOrder order = new InboundOrder();
                order.setWarehouseId(1L);
                order.setInboundType(type.getCode());

                assertThatCode(() -> service.createInboundOrder(order, List.of(inboundDetail())))
                        .as("合法入库类型 %s 应被接受", type.getCode())
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("✅ 类型大小写不敏感：purchase 与 PURCHASE 都接受")
        void inboundTypeCaseInsensitive() {
            InboundOrder lower = new InboundOrder();
            lower.setWarehouseId(1L);
            lower.setInboundType("purchase");

            assertThatCode(() -> service.createInboundOrder(lower, List.of(inboundDetail())))
                    .doesNotThrowAnyException();

            // 🔴 **必须写回归一化后的值** —— 这是本用例真正的价值：
            //    `InboundType.getByCode` 是**精确匹配**（`type.code.equals(code)`），
            //    如果只校验不写回，"purchase" 会**通过校验**但以**小写**落库
            //    → 后续 `getByCode("purchase")` 返回 null
            //    → 按类型分组的报表**照样漏掉这条单据** ——
            //    「校验放行了但没治好」比「不校验」更坏（让人以为防住了）。
            assertThat(lower.getInboundType())
                    .as("小写输入必须归一化成大写落库")
                    .isEqualTo(InboundType.PURCHASE.getCode());
            assertThat(InboundType.getByCode(lower.getInboundType()))
                    .as("归一化后必须能被 getByCode 查到")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("出库类型 outbound_type")
    class Outbound {

        @Test
        @DisplayName("🔴 类型值拼错 → 报业务异常，不落库")
        void outboundTypeTypoRejected() {
            OutboundOrder order = new OutboundOrder();
            order.setWarehouseId(1L);
            order.setOutboundType("SALEE");   // ← SALE 的拼写错误

            assertThatThrownBy(() -> service.createOutboundOrder(order, List.of(outboundDetail())))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("出库类型");

            verify(outboundOrderMapper, never()).insert(any());
        }

        @Test
        @DisplayName("✅ 类型值为空 → 归到 OTHER（DDL 可空，平台同步建单不带类型）")
        void outboundTypeBlankAllowed() {
            OutboundOrder order = new OutboundOrder();
            order.setWarehouseId(1L);
            order.setOutboundType("");

            assertThatCode(() -> service.createOutboundOrder(order, List.of(outboundDetail())))
                    .doesNotThrowAnyException();

            assertThat(order.getOutboundType())
                    .as("空类型必须落成 OTHER，不能是空串/null")
                    .isEqualTo(OutboundType.OTHER.getCode());
        }

        @Test
        @DisplayName("✅ 类型为 null → 归到 OTHER（同样按未指定处理）")
        void outboundTypeNullAllowed() {
            OutboundOrder order = new OutboundOrder();
            order.setWarehouseId(1L);
            order.setOutboundType(null);

            assertThatCode(() -> service.createOutboundOrder(order, List.of(outboundDetail())))
                    .doesNotThrowAnyException();

            assertThat(order.getOutboundType())
                    .as("null 类型必须落成 OTHER")
                    .isEqualTo(OutboundType.OTHER.getCode());
        }

        @Test
        @DisplayName("✅ 全部合法枚举值都放行")
        void allLegalOutboundTypesAccepted() {
            for (OutboundType type : OutboundType.values()) {
                OutboundOrder order = new OutboundOrder();
                order.setWarehouseId(1L);
                order.setOutboundType(type.getCode());

                assertThatCode(() -> service.createOutboundOrder(order, List.of(outboundDetail())))
                        .as("合法出库类型 %s 应被接受", type.getCode())
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("✅ 类型大小写不敏感：sale 与 SALE 都接受")
        void outboundTypeCaseInsensitive() {
            OutboundOrder lower = new OutboundOrder();
            lower.setWarehouseId(1L);
            lower.setOutboundType("sale");

            assertThatCode(() -> service.createOutboundOrder(lower, List.of(outboundDetail())))
                    .doesNotThrowAnyException();

            // 🔴 同入库：必须写回归一化后的值，否则小写落库后 getByCode 查不到
            assertThat(lower.getOutboundType())
                    .as("小写输入必须归一化成大写落库")
                    .isEqualTo(OutboundType.SALE.getCode());
            assertThat(OutboundType.getByCode(lower.getOutboundType()))
                    .as("归一化后必须能被 getByCode 查到")
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("📌 枚举 getByCode 对未知值返回 null（不是抛异常）")
    void getByCodeReturnsNullForUnknown() {
        assertThat(InboundType.getByCode("RETRUN")).isNull();
        assertThat(OutboundType.getByCode("SALEE")).isNull();
    }
}
