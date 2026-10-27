package com.crossborder.erp.warehouse;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BusinessException;
import com.crossborder.erp.warehouse.entity.InboundDetail;
import com.crossborder.erp.warehouse.entity.InboundOrder;
import com.crossborder.erp.warehouse.entity.InventoryBatch;
import com.crossborder.erp.warehouse.entity.OutboundDetail;
import com.crossborder.erp.warehouse.entity.OutboundOrder;
import com.crossborder.erp.warehouse.entity.PackingMaterial;
import com.crossborder.erp.warehouse.entity.StockTransfer;
import com.crossborder.erp.warehouse.entity.Warehouse;
import com.crossborder.erp.warehouse.entity.WarehouseLocation;
import com.crossborder.erp.warehouse.entity.WavePicking;
import com.crossborder.erp.warehouse.entity.WavePickingDetail;
import com.crossborder.erp.warehouse.enums.InboundType;
import com.crossborder.erp.warehouse.enums.OutboundType;
import com.crossborder.erp.warehouse.enums.PickStatus;
import com.crossborder.erp.warehouse.enums.WarehouseStatus;
import com.crossborder.erp.warehouse.enums.WaveStatus;
import com.crossborder.erp.warehouse.mapper.InboundDetailMapper;
import com.crossborder.erp.warehouse.mapper.InboundOrderMapper;
import com.crossborder.erp.warehouse.mapper.InventoryBatchMapper;
import com.crossborder.erp.warehouse.mapper.OutboundDetailMapper;
import com.crossborder.erp.warehouse.mapper.OutboundOrderMapper;
import com.crossborder.erp.warehouse.mapper.PackingMaterialMapper;
import com.crossborder.erp.warehouse.mapper.StockTransferMapper;
import com.crossborder.erp.warehouse.mapper.WarehouseLocationMapper;
import com.crossborder.erp.warehouse.mapper.WarehouseMapper;
import com.crossborder.erp.warehouse.mapper.WavePickingDetailMapper;
import com.crossborder.erp.warehouse.mapper.WavePickingMapper;
import com.crossborder.erp.warehouse.service.impl.WarehouseServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 仓库服务单元测试
 *
 * <p>使用 Mockito 模拟各 Mapper，不依赖数据库与 Spring 容器。</p>
 */
@SuppressWarnings("unchecked")
public class WarehouseServiceTest {

    private WarehouseMapper warehouseMapper;
    private WarehouseLocationMapper locationMapper;
    private InventoryBatchMapper batchMapper;
    private PackingMaterialMapper packingMaterialMapper;
    private InboundOrderMapper inboundOrderMapper;
    private InboundDetailMapper inboundDetailMapper;
    private OutboundOrderMapper outboundOrderMapper;
    private OutboundDetailMapper outboundDetailMapper;
    private StockTransferMapper transferMapper;
    private WavePickingMapper waveMapper;
    private WavePickingDetailMapper waveDetailMapper;

    private WarehouseServiceImpl warehouseService;

    /** 内存态仓库数据 */
    private final Map<Long, Warehouse> warehouseStore = new HashMap<>();
    /** 内存态入库单数据 */
    private final Map<Long, InboundOrder> inboundOrderStore = new HashMap<>();
    /** 内存态入库明细数据 */
    private final List<InboundDetail> inboundDetailStore = new ArrayList<>();
    /** 内存态波次数据 */
    private final Map<Long, WavePicking> waveStore = new HashMap<>();
    /** 内存态波次明细数据 */
    private final List<WavePickingDetail> waveDetailStore = new ArrayList<>();

    /** 解析 MyBatis-Plus 生成的条件片段，如 (wave_id = #{ew.paramNameValuePairs.MPGENVAL1}) */
    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "\\(?\\s*(\\w+)\\s*(!=|<>|=)\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}\\s*\\)?");

    private long idGenerator = 1000L;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析字段名依赖 MyBatis-Plus 的 TableInfo / Lambda 缓存
        initTableInfo(Warehouse.class);
        initTableInfo(WarehouseLocation.class);
        initTableInfo(InventoryBatch.class);
        initTableInfo(PackingMaterial.class);
        initTableInfo(InboundOrder.class);
        initTableInfo(InboundDetail.class);
        initTableInfo(OutboundOrder.class);
        initTableInfo(OutboundDetail.class);
        initTableInfo(StockTransfer.class);
        initTableInfo(WavePicking.class);
        initTableInfo(WavePickingDetail.class);
    }

    private static void initTableInfo(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(
                "com.crossborder.erp.warehouse.mapper." + entityClass.getSimpleName() + "Mapper");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    @BeforeEach
    void setUp() {
        warehouseMapper = mock(WarehouseMapper.class);
        locationMapper = mock(WarehouseLocationMapper.class);
        batchMapper = mock(InventoryBatchMapper.class);
        packingMaterialMapper = mock(PackingMaterialMapper.class);
        inboundOrderMapper = mock(InboundOrderMapper.class);
        inboundDetailMapper = mock(InboundDetailMapper.class);
        outboundOrderMapper = mock(OutboundOrderMapper.class);
        outboundDetailMapper = mock(OutboundDetailMapper.class);
        transferMapper = mock(StockTransferMapper.class);
        waveMapper = mock(WavePickingMapper.class);
        waveDetailMapper = mock(WavePickingDetailMapper.class);

        warehouseService = new WarehouseServiceImpl(
                warehouseMapper, locationMapper, batchMapper, packingMaterialMapper,
                inboundOrderMapper, inboundDetailMapper, outboundOrderMapper, outboundDetailMapper, transferMapper,
                waveMapper, waveDetailMapper);

        // 仓库：insert 生成主键，selectById / selectList / selectPage 走内存态
        doAnswer(invocation -> {
            Warehouse warehouse = invocation.getArgument(0);
            if (warehouse.getId() == null) {
                warehouse.setId(++idGenerator);
            }
            warehouseStore.put(warehouse.getId(), warehouse);
            return 1;
        }).when(warehouseMapper).insert(any(Warehouse.class));

        doAnswer(invocation -> {
            Warehouse warehouse = invocation.getArgument(0);
            warehouseStore.put(warehouse.getId(), warehouse);
            return 1;
        }).when(warehouseMapper).updateById(any(Warehouse.class));

        when(warehouseMapper.selectById(anyLong()))
                .thenAnswer(invocation -> warehouseStore.get(invocation.<Long>getArgument(0)));
        when(warehouseMapper.selectList(any()))
                .thenAnswer(invocation -> new ArrayList<>(warehouseStore.values()));
        when(warehouseMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<Warehouse> page = invocation.getArgument(0);
            page.setRecords(new ArrayList<>(warehouseStore.values()));
            return page;
        });
        when(warehouseMapper.selectCount(any())).thenReturn(0L);

        // 库位：默认空库位
        when(locationMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(locationMapper.selectCount(any())).thenReturn(0L);

        // 入库单
        doAnswer(invocation -> {
            InboundOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(++idGenerator);
            }
            inboundOrderStore.put(order.getId(), order);
            return 1;
        }).when(inboundOrderMapper).insert(any(InboundOrder.class));

        when(inboundOrderMapper.selectById(anyLong()))
                .thenAnswer(invocation -> inboundOrderStore.get(invocation.<Long>getArgument(0)));
        when(inboundOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<InboundOrder> page = invocation.getArgument(0);
            page.setRecords(new ArrayList<>(inboundOrderStore.values()));
            return page;
        });

        // 入库明细
        doAnswer(invocation -> {
            InboundDetail detail = invocation.getArgument(0);
            if (detail.getId() == null) {
                detail.setId(++idGenerator);
            }
            inboundDetailStore.add(detail);
            return 1;
        }).when(inboundDetailMapper).insert(any(InboundDetail.class));

        when(inboundDetailMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(inboundDetailStore));

        // 出库单
        doAnswer(invocation -> {
            OutboundOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(++idGenerator);
            }
            return 1;
        }).when(outboundOrderMapper).insert(any(OutboundOrder.class));

        doAnswer(invocation -> {
            OutboundDetail detail = invocation.getArgument(0);
            if (detail.getId() == null) {
                detail.setId(++idGenerator);
            }
            return 1;
        }).when(outboundDetailMapper).insert(any(OutboundDetail.class));

        // 波次主表：内存态
        doAnswer(invocation -> {
            WavePicking wave = invocation.getArgument(0);
            if (wave.getId() == null) {
                wave.setId(++idGenerator);
            }
            waveStore.put(wave.getId(), wave);
            return 1;
        }).when(waveMapper).insert(any(WavePicking.class));

        doAnswer(invocation -> {
            WavePicking wave = invocation.getArgument(0);
            waveStore.put(wave.getId(), wave);
            return 1;
        }).when(waveMapper).updateById(any(WavePicking.class));

        when(waveMapper.selectById(anyLong()))
                .thenAnswer(invocation -> waveStore.get(invocation.<Long>getArgument(0)));

        // 波次明细：内存态
        doAnswer(invocation -> {
            WavePickingDetail detail = invocation.getArgument(0);
            if (detail.getId() == null) {
                detail.setId(++idGenerator);
            }
            waveDetailStore.add(detail);
            return 1;
        }).when(waveDetailMapper).insert(any(WavePickingDetail.class));

        doAnswer(invocation -> {
            WavePickingDetail detail = invocation.getArgument(0);
            // 原地替换，保持内存列表顺序稳定
            for (int i = 0; i < waveDetailStore.size(); i++) {
                if (waveDetailStore.get(i).getId().equals(detail.getId())) {
                    waveDetailStore.set(i, detail);
                    return 1;
                }
            }
            waveDetailStore.add(detail);
            return 1;
        }).when(waveDetailMapper).updateById(any(WavePickingDetail.class));

        when(waveDetailMapper.selectById(anyLong()))
                .thenAnswer(invocation -> waveDetailStore.stream()
                        .filter(d -> d.getId().equals(invocation.<Long>getArgument(0)))
                        .findFirst().orElse(null));

        // selectList 走内存态：按 wrapper 条件真实过滤
        when(waveDetailMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<WavePickingDetail> wrapper = invocation.getArgument(0);
            return applyWaveDetailWrapper(wrapper);
        });
    }

    /**
     * 在内存中模拟 LambdaQueryWrapper 对 wms_wave_picking_detail 的过滤
     *
     * <p>仅支持本测试用到的等值 / 不等值条件（wave_id、pick_status）。</p>
     */
    private List<WavePickingDetail> applyWaveDetailWrapper(LambdaQueryWrapper<WavePickingDetail> wrapper) {
        String segment = wrapper.getSqlSegment();
        Map<String, Object> params = wrapper.getParamNameValuePairs();

        List<WavePickingDetail> result = new ArrayList<>(waveDetailStore);
        for (String conjunct : segment.split("(?i)AND")) {
            String trimmed = conjunct.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = CONDITION_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            String column = matcher.group(1);
            String operator = matcher.group(2);
            String paramKey = matcher.group(3);
            Object value = params.get(paramKey);
            if (value == null) {
                continue;
            }

            if ("wave_id".equals(column)) {
                result.removeIf(d -> !Objects.equals(d.getWaveId(), value));
            } else if ("pick_status".equals(column)) {
                if ("=".equals(operator)) {
                    result.removeIf(d -> !Objects.equals(d.getPickStatus(), value));
                } else {
                    result.removeIf(d -> Objects.equals(d.getPickStatus(), value));
                }
            }
        }
        return result;
    }

    @Test
    @DisplayName("创建仓库：自动生成编码与状态并返回主键")
    public void testCreateWarehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setName("测试仓库");
        warehouse.setAddress("测试地址");
        warehouse.setContact("测试管理员");
        warehouse.setPhone("13800138000");

        Long warehouseId = warehouseService.createWarehouse(warehouse);
        assertNotNull(warehouseId);
        assertTrue(warehouseId > 0);
        assertTrue(warehouse.getWarehouseCode().startsWith("WH"));
        assertEquals(WarehouseStatus.ACTIVE.getCode(), warehouse.getStatus());
        assertNotNull(warehouse.getCreateTime());
        assertNotNull(warehouse.getUpdateTime());

        Warehouse created = warehouseService.getWarehouse(warehouseId);
        assertNotNull(created);
        assertEquals("测试仓库", created.getName());
    }

    @Test
    @DisplayName("查询仓库列表：返回全部仓库")
    public void testListWarehouses() {
        Warehouse warehouse = new Warehouse();
        warehouse.setName("列表仓库");
        warehouseService.createWarehouse(warehouse);

        List<Warehouse> warehouses = warehouseService.listWarehouses();
        assertNotNull(warehouses);
        assertTrue(!warehouses.isEmpty());
    }

    @Test
    @DisplayName("创建入库单：汇总总数并回写明细主单ID")
    public void testCreateInboundOrder() {
        InboundOrder order = new InboundOrder();
        order.setWarehouseId(1L);
        order.setSupplierName("测试供应商");
        order.setInboundType(InboundType.PURCHASE.getCode());
        order.setRemark("测试入库单");

        InboundDetail detail = new InboundDetail();
        detail.setProductId(1L);
        detail.setQuantity(100);
        detail.setUnitCost(BigDecimal.valueOf(10.0));
        detail.setLocationCode("A-01-01");

        Long orderId = warehouseService.createInboundOrder(order, List.of(detail));

        assertNotNull(orderId);
        assertTrue(orderId > 0);
        assertEquals(100, order.getTotalQuantity());
        assertEquals(0, order.getStatus());
        assertTrue(order.getOrderNo().startsWith("IN"));
        assertEquals(orderId, detail.getInboundOrderId());
        verify(inboundOrderMapper).insert(any(InboundOrder.class));
        verify(inboundDetailMapper).insert(any(InboundDetail.class));
    }

    @Test
    @DisplayName("分页查询入库单：返回分页结果")
    public void testListInboundOrders() {
        InboundOrder order = new InboundOrder();
        order.setWarehouseId(1L);
        order.setInboundType(InboundType.PURCHASE.getCode());
        warehouseService.createInboundOrder(order, List.of());

        Page<InboundOrder> page = warehouseService.listInboundOrders(1L, "1", 1, 10);
        assertNotNull(page);
        assertNotNull(page.getRecords());
        assertTrue(page.getRecords().size() >= 1);
    }

    @Test
    @DisplayName("更新仓库：更新联系人与更新时间")
    public void testUpdateWarehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setName("待更新仓库");
        warehouse.setContact("原管理员");
        Long warehouseId = warehouseService.createWarehouse(warehouse);

        warehouse.setContact("新管理员");
        warehouseService.updateWarehouse(warehouse);

        Warehouse updated = warehouseService.getWarehouse(warehouseId);
        assertNotNull(updated);
        assertEquals("新管理员", updated.getContact());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    @DisplayName("创建出库单：写入单据并锁定库位库存")
    public void testCreateOutboundOrder() {
        WarehouseLocation location = new WarehouseLocation();
        location.setWarehouseId(1L);
        location.setProductId(1L);
        location.setLocationCode("A-01-01");
        location.setStockQuantity(100);
        location.setLockedQuantity(0);
        when(locationMapper.selectOne(any())).thenReturn(location);

        OutboundOrder order = new OutboundOrder();
        order.setWarehouseId(1L);
        order.setOutboundType(OutboundType.SALE.getCode());
        order.setCustomerName("测试客户");

        OutboundDetail detail = new OutboundDetail();
        detail.setProductId(1L);
        detail.setQuantity(10);
        detail.setLocationCode("A-01-01");

        Long orderId = warehouseService.createOutboundOrder(order, List.of(detail));

        assertNotNull(orderId);
        assertTrue(orderId > 0);
        assertEquals(10, order.getTotalQuantity());
        assertEquals(0, order.getStatus());
        assertEquals(10, location.getLockedQuantity());
        verify(outboundOrderMapper).insert(any(OutboundOrder.class));
        verify(outboundDetailMapper).insert(any(OutboundDetail.class));
    }

    @Test
    @DisplayName("出入库类型枚举：可按编码解析中文描述")
    public void testTypeEnums() {
        assertEquals(InboundType.PURCHASE, InboundType.getByCode("PURCHASE"));
        assertEquals("采购入库", InboundType.getDescription("PURCHASE"));
        assertNull(InboundType.getByCode("NOT_EXIST"));

        assertEquals(OutboundType.SALE, OutboundType.getByCode("SALE"));
        assertEquals("销售出库", OutboundType.getDescription("SALE"));
        assertNull(OutboundType.getByCode("NOT_EXIST"));
    }

    // ==================== 波次拣货 ====================

    /**
     * 构造一个包含两条明细的波次
     */
    private Long createWave() {
        WavePicking wave = new WavePicking();
        wave.setWaveType("ORDER");
        wave.setPriority("HIGH");

        WavePickingDetail d1 = new WavePickingDetail();
        d1.setOrderId(11L);
        d1.setOrderNo("SO-001");
        d1.setProductId(1L);
        d1.setPickQuantity(10);

        WavePickingDetail d2 = new WavePickingDetail();
        d2.setOrderId(12L);
        d2.setOrderNo("SO-002");
        d2.setProductId(2L);
        d2.setPickQuantity(5);

        return warehouseService.createWavePicking(wave, List.of(d1, d2));
    }

    @Test
    @DisplayName("创建波次：生成波次号并汇总订单数/SKU数/总数量")
    public void testCreateWavePicking() {
        Long waveId = createWave();

        assertNotNull(waveId);
        WavePicking wave = warehouseService.getWavePicking(waveId);
        assertNotNull(wave);
        assertTrue(wave.getWaveNo().startsWith("WAVE"));
        assertEquals(WaveStatus.CREATED.getCode(), wave.getWaveStatus());
        assertEquals(2, wave.getOrderCount());
        assertEquals(2, wave.getSkuCount());
        assertEquals(15, wave.getTotalQuantity());
        assertEquals(0, wave.getPickedQuantity());
        assertEquals(2, waveDetailStore.size());
        verify(waveMapper).insert(any(WavePicking.class));
        verify(waveDetailMapper, times(2)).insert(any(WavePickingDetail.class));
    }

    @Test
    @DisplayName("创建波次：明细为空时抛出业务异常")
    public void testCreateWavePickingWithEmptyDetails() {
        assertThrows(BusinessException.class, () -> warehouseService.createWavePicking(new WavePicking(), List.of()));
    }

    @Test
    @DisplayName("波次全流程：分配 → 开始 → 拣货 → 完成")
    public void testWavePickingLifecycle() {
        Long waveId = createWave();

        warehouseService.assignWavePicking(waveId, "picker01");
        WavePicking assigned = warehouseService.getWavePicking(waveId);
        assertEquals(WaveStatus.ASSIGNED.getCode(), assigned.getWaveStatus());
        assertEquals("picker01", assigned.getAssignUser());
        assertNotNull(assigned.getAssignTime());

        warehouseService.startWavePicking(waveId);
        WavePicking started = warehouseService.getWavePicking(waveId);
        assertEquals(WaveStatus.PICKING.getCode(), started.getWaveStatus());
        assertNotNull(started.getStartTime());
        // 待拣明细同步转为拣货中
        assertTrue(waveDetailStore.stream()
                .allMatch(d -> PickStatus.PICKING.getCode().equals(d.getPickStatus())));

        // 全部拣完
        for (WavePickingDetail detail : new ArrayList<>(waveDetailStore)) {
            warehouseService.confirmPick(detail.getId(), detail.getPickQuantity(), "picker01");
        }
        WavePicking picked = warehouseService.getWavePicking(waveId);
        assertEquals(15, picked.getPickedQuantity());
        assertTrue(waveDetailStore.stream()
                .allMatch(d -> PickStatus.PICKED.getCode().equals(d.getPickStatus())));

        warehouseService.completeWavePicking(waveId);
        WavePicking completed = warehouseService.getWavePicking(waveId);
        assertEquals(WaveStatus.COMPLETED.getCode(), completed.getWaveStatus());
        assertNotNull(completed.getCompleteTime());
    }

    @Test
    @DisplayName("波次状态校验：非已创建不能分配，非已分配不能开始")
    public void testWavePickingStatusGuard() {
        Long waveId = createWave();

        // 未分配直接开始 → 异常
        assertThrows(BusinessException.class, () -> warehouseService.startWavePicking(waveId));

        warehouseService.assignWavePicking(waveId, "picker01");
        // 重复分配 → 异常
        assertThrows(BusinessException.class, () -> warehouseService.assignWavePicking(waveId, "picker02"));

        // 不存在的波次 → 异常
        assertThrows(BusinessException.class, () -> warehouseService.assignWavePicking(999999L, "picker01"));
    }

    @Test
    @DisplayName("确认拣货：数量超限或负数抛出业务异常")
    public void testConfirmPickValidation() {
        Long waveId = createWave();
        warehouseService.assignWavePicking(waveId, "picker01");
        warehouseService.startWavePicking(waveId);

        WavePickingDetail detail = waveDetailStore.get(0);

        assertThrows(BusinessException.class,
                () -> warehouseService.confirmPick(detail.getId(), detail.getPickQuantity() + 1, "picker01"));
        assertThrows(BusinessException.class,
                () -> warehouseService.confirmPick(detail.getId(), -1, "picker01"));
        assertThrows(BusinessException.class,
                () -> warehouseService.confirmPick(999999L, 1, "picker01"));

        // 合法数量：部分拣货 → 状态仍为拣货中
        warehouseService.confirmPick(detail.getId(), 3, "picker01");
        assertEquals(3, waveDetailStore.get(0).getPickedQuantity());
        assertEquals(PickStatus.PICKING.getCode(), waveDetailStore.get(0).getPickStatus());
        // 已拣数量只统计 PICKED 明细
        assertEquals(0, warehouseService.getWavePicking(waveId).getPickedQuantity());
    }

    @Test
    @DisplayName("完成波次：存在未完成明细时拒绝完成")
    public void testCompleteWavePickingWithUnfinishedDetails() {
        Long waveId = createWave();
        warehouseService.assignWavePicking(waveId, "picker01");
        warehouseService.startWavePicking(waveId);

        assertThrows(BusinessException.class, () -> warehouseService.completeWavePicking(waveId));
    }

    @Test
    @DisplayName("取消波次：未完成明细标记为已取消，波次状态置为已取消")
    public void testCancelWavePicking() {
        Long waveId = createWave();
        warehouseService.assignWavePicking(waveId, "picker01");
        warehouseService.startWavePicking(waveId);

        warehouseService.cancelWavePicking(waveId, "库存不足");

        WavePicking cancelled = warehouseService.getWavePicking(waveId);
        assertEquals(WaveStatus.CANCELLED.getCode(), cancelled.getWaveStatus());
        assertEquals("库存不足", cancelled.getRemark());
        assertTrue(waveDetailStore.stream()
                .allMatch(d -> PickStatus.CANCELLED.getCode().equals(d.getPickStatus())));

        // 重复取消 → 异常
        assertThrows(BusinessException.class, () -> warehouseService.cancelWavePicking(waveId, "重复"));
    }

    @Test
    @DisplayName("波次状态枚举：可按编码解析中文描述")
    public void testWaveStatusEnums() {
        assertEquals(WaveStatus.CREATED, WaveStatus.getByCode("CREATED"));
        assertEquals("已创建", WaveStatus.getDescription("CREATED"));
        assertNull(WaveStatus.getByCode("NOT_EXIST"));

        assertEquals(PickStatus.PENDING, PickStatus.getByCode("PENDING"));
        assertEquals("待拣货", PickStatus.getDescription("PENDING"));
        assertNull(PickStatus.getByCode("NOT_EXIST"));
    }
}
