package com.crossborder.erp.warehouse;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crossborder.erp.common.exception.BusinessException;
import com.crossborder.erp.warehouse.entity.InventoryCheck;
import com.crossborder.erp.warehouse.entity.InventoryCheckResult;
import com.crossborder.erp.warehouse.entity.Warehouse;
import com.crossborder.erp.warehouse.entity.WarehouseLocation;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 库存盘点流程测试
 *
 * <p>🔴 <b>修复前的空壳</b>：</p>
 * <ul>
 *   <li>{@code createInventoryCheck} 方法体只有一行 {@code log.info}，
 *       **什么都不做**却返回「成功」→ 前端点「创建盘点单」，
 *       接口 200，库里一行数据都没有</li>
 *   <li>{@code confirmInventoryCheck} 的 checkId <b>完全不校验</b>（传 0 也能「确认」），
 *       而且不落库任何东西 → 盘点做完数据就没了</li>
 *   <li>没有 {@code wms_inventory_check} 主表，
 *       也没有任何<b>查询</b>盘点单的接口 → 就算能建也看不到</li>
 * </ul>
 *
 * <p>本类把这些逐条锁死。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@SuppressWarnings("unchecked")
class InventoryCheckFlowTest {

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
    private InventoryCheckMapper inventoryCheckMapper;
    private InventoryCheckResultMapper inventoryCheckResultMapper;

    private WarehouseServiceImpl warehouseService;

    private final Map<Long, Warehouse> warehouseStore = new HashMap<>();
    private final Map<Long, InventoryCheck> checkStore = new HashMap<>();
    private final Map<Long, InventoryCheckResult> resultStore = new HashMap<>();
    private final List<WarehouseLocation> locationStore = new ArrayList<>();

    private long idGenerator = 90000L;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(Warehouse.class);
        initTableInfo(WarehouseLocation.class);
        initTableInfo(InventoryCheck.class);
        initTableInfo(InventoryCheckResult.class);
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
        inventoryCheckMapper = mock(InventoryCheckMapper.class);
        inventoryCheckResultMapper = mock(InventoryCheckResultMapper.class);

        warehouseService = new WarehouseServiceImpl(
                warehouseMapper, locationMapper, batchMapper, packingMaterialMapper,
                inboundOrderMapper, inboundDetailMapper, outboundOrderMapper, outboundDetailMapper, transferMapper,
                waveMapper, waveDetailMapper, inventoryCheckMapper, inventoryCheckResultMapper);

        // 仓库
        Warehouse wh = new Warehouse();
        wh.setId(1L);
        wh.setName("上海中心仓");
        wh.setStatus(1);
        warehouseStore.put(1L, wh);
        when(warehouseMapper.selectById(anyLong()))
                .thenAnswer(inv -> warehouseStore.get(inv.<Long>getArgument(0)));

        // 库位
        // 📌 mock 必须**真的按 wrapper 条件过滤**，不能无脑返回全部：
        //    service 依赖「查询结果条数」做校验（库位不属于该仓库 → 报错），
        //    mock 若不过滤，这些校验就形同虚设（测试会假绿）。
        when(locationMapper.selectList(any())).thenAnswer(inv -> {
            List<Object> vals = orderedValues(inv.<LambdaQueryWrapper<WarehouseLocation>>getArgument(0));
            List<WarehouseLocation> out = new ArrayList<>();
            for (WarehouseLocation l : locationStore) {
                if (vals.size() >= 1 && !Objects.equals(vals.get(0), l.getWarehouseId())) {
                    continue;
                }
                if (vals.size() >= 2 && Objects.equals(vals.get(1), l.getStatus())) {
                    continue;   // ne(status, 'disabled')
                }
                out.add(l);
            }
            return out;
        });
        when(locationMapper.selectOne(any())).thenAnswer(inv -> {
            List<Object> vals = orderedValues(inv.<LambdaQueryWrapper<WarehouseLocation>>getArgument(0));
            for (WarehouseLocation l : locationStore) {
                if (vals.size() >= 3
                        && Objects.equals(vals.get(0), l.getWarehouseId())
                        && Objects.equals(vals.get(1), l.getProductId())
                        && Objects.equals(vals.get(2), l.getLocationCode())) {
                    return l;
                }
            }
            return null;
        });
        when(locationMapper.selectById(anyLong()))
                .thenAnswer(inv -> locationStore.stream()
                        .filter(l -> l.getId().equals(inv.<Long>getArgument(0)))
                        .findFirst().orElse(null));
        doAnswer(inv -> {
            WarehouseLocation loc = inv.getArgument(0);
            if (loc.getId() == null) {
                loc.setId(++idGenerator);
            }
            locationStore.add(loc);
            return 1;
        }).when(locationMapper).insert(any(WarehouseLocation.class));
        doAnswer(inv -> {
            WarehouseLocation loc = inv.getArgument(0);
            for (int i = 0; i < locationStore.size(); i++) {
                if (locationStore.get(i).getId().equals(loc.getId())) {
                    locationStore.set(i, loc);
                }
            }
            return 1;
        }).when(locationMapper).updateById(any(WarehouseLocation.class));

        // 盘点单
        doAnswer(inv -> {
            InventoryCheck c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(++idGenerator);
            }
            checkStore.put(c.getId(), c);
            return 1;
        }).when(inventoryCheckMapper).insert(any(InventoryCheck.class));
        doAnswer(inv -> {
            InventoryCheck c = inv.getArgument(0);
            checkStore.put(c.getId(), c);
            return 1;
        }).when(inventoryCheckMapper).updateById(any(InventoryCheck.class));
        when(inventoryCheckMapper.selectById(anyLong()))
                .thenAnswer(inv -> checkStore.get(inv.<Long>getArgument(0)));
        when(inventoryCheckMapper.selectList(any()))
                .thenAnswer(inv -> new ArrayList<>(checkStore.values()));

        // 盘点明细
        doAnswer(inv -> {
            InventoryCheckResult r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(++idGenerator);
            }
            resultStore.put(r.getId(), r);
            return 1;
        }).when(inventoryCheckResultMapper).insert(any(InventoryCheckResult.class));
        doAnswer(inv -> {
            InventoryCheckResult r = inv.getArgument(0);
            resultStore.put(r.getId(), r);
            return 1;
        }).when(inventoryCheckResultMapper).updateById(any(InventoryCheckResult.class));
        when(inventoryCheckResultMapper.selectList(any()))
                .thenAnswer(inv -> new ArrayList<>(resultStore.values()));
    }

    /**
     * 按 SQL 段里占位符的**出现顺序**取出 wrapper 的参数值。
     *
     * <p>📌 两个坑：</p>
     * <ol>
     *   <li>{@code getParamNameValuePairs()} 在调 {@code getSqlSegment()} 渲染前是**空的</li>
     *   <li>它是 **HashMap**，迭代顺序不保证 == 调用顺序
     *       （实测 MPGENVAL2 排在 MPGENVAL1 前面）
     *       → 直接 values() 取会拿到错位的参数</li>
     * </ol>
     * <p>所以从渲染出的 SQL 里按 {@code MPGENVALn} 的出现顺序回查 map。</p>
     */
    private static List<Object> orderedValues(LambdaQueryWrapper<?> wrapper) {
        String sql = wrapper.getSqlSegment();   // 触发渲染，填充 paramNameValuePairs
        Map<String, Object> params = wrapper.getParamNameValuePairs();
        List<Object> ordered = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(sql);
        while (m.find()) {
            ordered.add(params.get(m.group()));   // group() = "MPGENVAL1"，就是 map 的 key
        }
        return ordered;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("MPGENVAL\\d+");

    private WarehouseLocation location(long id, long whId, String code, int stock) {
        WarehouseLocation loc = new WarehouseLocation();
        loc.setId(id);
        loc.setWarehouseId(whId);
        loc.setLocationCode(code);
        loc.setProductId(1001L);
        loc.setStockQuantity(stock);
        loc.setCurrentCapacity(stock);
        loc.setLockedQuantity(0);
        // 📌 库位 status 是 VARCHAR（AVAILABLE/OCCUPIED/disabled），不是数字
        loc.setStatus("OCCUPIED");
        return loc;
    }

    // ============================================================
    // 创建盘点单：以前是空壳
    // ============================================================

    @Test
    @DisplayName("创建盘点单：真正落库 + 生成待盘明细行（回归：以前只打日志）")
    void createCheckPersistsCheckAndRows() {
        locationStore.add(location(1L, 1L, "A-01-01-01", 400));
        locationStore.add(location(2L, 1L, "A-01-01-02", 285));

        Long checkId = warehouseService.createInventoryCheck(1L, List.of(), "FULL", "月度全盘");

        assertNotNull(checkId, "必须返回盘点单 ID（前端要拿它调 confirm）");
        InventoryCheck check = checkStore.get(checkId);
        assertNotNull(check, "盘点单没落库");
        assertEquals("CREATED", check.getCheckStatus());
        assertEquals("FULL", check.getCheckType());
        assertEquals(1L, check.getWarehouseId());
        assertEquals(2, check.getLocationCount());
        assertNotNull(check.getCheckNo());

        // 每个库位生成一行待盘明细
        assertEquals(2, resultStore.size());
        InventoryCheckResult row = resultStore.values().iterator().next();
        assertEquals(checkId, row.getCheckId());
        assertEquals("PENDING", row.getStatus());
        assertEquals(0, row.getDiffQty());
    }

    @Test
    @DisplayName("创建盘点单：只盘指定库位")
    void createCheckWithSpecificLocations() {
        locationStore.add(location(1L, 1L, "A-01-01-01", 400));
        locationStore.add(location(2L, 1L, "A-01-01-02", 285));
        locationStore.add(location(3L, 1L, "B-02-01-01", 1660));

        Long checkId = warehouseService.createInventoryCheck(1L, List.of(3L), "SPOT", "抽盘");

        assertEquals(1, checkStore.get(checkId).getLocationCount());
        assertEquals(1, resultStore.size());
    }

    @Test
    @DisplayName("创建盘点单：库位不属于该仓库 → 报错而不是默默只盘一部分")
    void createCheckRejectsForeignLocations() {
        locationStore.add(location(1L, 1L, "A-01-01-01", 400));
        locationStore.add(location(2L, 2L, "A-01-01-02", 285));  // 属于仓库 2

        BusinessException e = assertThrows(BusinessException.class,
                () -> warehouseService.createInventoryCheck(1L, List.of(1L, 2L), "FULL", null));
        assertTrue(e.getMessage().contains("不属于该仓库"));
    }

    @Test
    @DisplayName("创建盘点单：仓库不存在 → 报错（不能生成指向不存在仓库的盘点单）")
    void createCheckRejectsUnknownWarehouse() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> warehouseService.createInventoryCheck(999L, List.of(), "FULL", null));
        assertTrue(e.getMessage().contains("仓库不存在"));
    }

    @Test
    @DisplayName("创建盘点单：没有可盘库位 → 报错（而不是建一个空盘点单）")
    void createCheckRejectsNoLocations() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> warehouseService.createInventoryCheck(1L, List.of(), "FULL", null));
        assertTrue(e.getMessage().contains("没有可盘点"));
    }

    @Test
    @DisplayName("创建盘点单：自动盘点跳过 disabled 库位")
    void createCheckSkipsDisabledLocations() {
        WarehouseLocation disabled = location(3L, 1L, "C-01-01-01", 100);
        disabled.setStatus("disabled");
        locationStore.add(location(1L, 1L, "A-01-01-01", 400));
        locationStore.add(disabled);

        Long checkId = warehouseService.createInventoryCheck(1L, List.of(), "FULL", null);
        assertEquals(1, checkStore.get(checkId).getLocationCount());
    }

    // ============================================================
    // 确认盘点：以前 checkId 完全不校验
    // ============================================================

    @Test
    @DisplayName("确认盘点：盘点单不存在 → 报错（回归：以前传 0 也能「确认」）")
    void confirmRejectsUnknownCheck() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> warehouseService.confirmInventoryCheck(0L, List.of()));
        assertTrue(e.getMessage().contains("盘点单不存在"));
    }

    @Test
    @DisplayName("确认盘点：已确认的单不能重复确认")
    void confirmRejectsDuplicate() {
        locationStore.add(location(1L, 1L, "A-01-01-01", 400));
        Long checkId = warehouseService.createInventoryCheck(1L, List.of(), "FULL", null);

        InventoryCheckResult row = new InventoryCheckResult();
        row.setCheckId(checkId);
        row.setWarehouseId(1L);
        row.setLocationId(1L);
        row.setLocationCode("A-01-01-01");
        row.setProductId(1001L);
        row.setBookQuantity(400);
        row.setActualQuantity(398);
        row.setDiffQty(-2);
        warehouseService.confirmInventoryCheck(checkId, List.of(row));

        BusinessException e = assertThrows(BusinessException.class,
                () -> warehouseService.confirmInventoryCheck(checkId, List.of()));
        assertTrue(e.getMessage().contains("已确认"));
    }

    @Test
    @DisplayName("确认盘点：汇总差异回盘点单（盘盈/盘亏分别统计）")
    void confirmAggregatesDifferences() {
        locationStore.add(location(1L, 1L, "A-01-01-01", 400));
        Long checkId = warehouseService.createInventoryCheck(1L, List.of(), "FULL", null);

        InventoryCheckResult loss = new InventoryCheckResult();
        loss.setCheckId(checkId);
        loss.setWarehouseId(1L);
        loss.setLocationId(1L);
        loss.setLocationCode("A-01-01-01");
        loss.setProductId(1001L);
        loss.setBookQuantity(400);
        loss.setActualQuantity(398);
        loss.setDiffQty(-2);

        InventoryCheckResult profit = new InventoryCheckResult();
        profit.setCheckId(checkId);
        profit.setWarehouseId(1L);
        profit.setLocationId(1L);
        profit.setLocationCode("A-01-01-01");
        profit.setProductId(1001L);
        profit.setBookQuantity(400);
        profit.setActualQuantity(405);
        profit.setDiffQty(5);

        warehouseService.confirmInventoryCheck(checkId, List.of(loss, profit));

        InventoryCheck check = checkStore.get(checkId);
        assertEquals("CONFIRMED", check.getCheckStatus());
        assertEquals(2, check.getDifferenceCount());
        assertEquals(5, check.getProfitQuantity());
        assertEquals(2, check.getLossQuantity());
        assertNotNull(check.getEndTime());
    }

    @Test
    @DisplayName("确认盘点：明细状态回写为 CONFIRMED")
    void confirmUpdatesRowStatus() {
        locationStore.add(location(1L, 1L, "A-01-01-01", 400));
        Long checkId = warehouseService.createInventoryCheck(1L, List.of(), "FULL", null);

        InventoryCheckResult row = new InventoryCheckResult();
        row.setCheckId(checkId);
        row.setWarehouseId(1L);
        row.setLocationId(1L);
        row.setLocationCode("A-01-01-01");
        row.setProductId(1001L);
        row.setBookQuantity(400);
        row.setActualQuantity(400);
        row.setDiffQty(0);

        warehouseService.confirmInventoryCheck(checkId, List.of(row));

        InventoryCheck check = checkStore.get(checkId);
        assertEquals("CONFIRMED", check.getCheckStatus());
        assertEquals(0, check.getDifferenceCount());
    }

    // ============================================================
    // 查询接口：以前一个都没有
    // ============================================================

    @Test
    @DisplayName("查询盘点单列表 / 详情 / 明细")
    void listAndGetChecks() {
        locationStore.add(location(1L, 1L, "A-01-01-01", 400));
        Long checkId = warehouseService.createInventoryCheck(1L, List.of(), "FULL", "测试盘点");

        List<InventoryCheck> list = warehouseService.listInventoryChecks(1L, null);
        assertEquals(1, list.size());
        assertEquals(checkId, list.get(0).getId());

        assertEquals(checkId, warehouseService.getInventoryCheck(checkId).getId());
        assertEquals(1, warehouseService.getInventoryCheckResults(checkId).size());

        assertThrows(BusinessException.class, () -> warehouseService.getInventoryCheck(12345L));
    }
}
