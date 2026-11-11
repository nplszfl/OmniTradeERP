package com.crossborder.pricing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.dto.PricingSuggestionVO;
import com.crossborder.pricing.entity.AiPricingSuggestion;
import com.crossborder.pricing.entity.PriceHistory;
import com.crossborder.pricing.entity.Product;
import com.crossborder.pricing.mapper.AiPricingSuggestionMapper;
import com.crossborder.pricing.mapper.PriceHistoryMapper;
import com.crossborder.pricing.mapper.ProductMapper;
import com.crossborder.pricing.service.PricingSuggestionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PricingSuggestionService 单元测试
 *
 * <p>覆盖前端 erp-web/src/api/ai.ts 的 pricingApi 调用的 4 个接口背后的逻辑：
 * listTasks / suggest / history / apply。</p>
 *
 * <p>📌 注意：断言 LambdaQueryWrapper 条件前必须先初始化 TableInfo，
 * 否则 {@code eq(SFunction, ...)} 找不到 lambda 缓存会抛异常。</p>
 */
@DisplayName("PricingSuggestionService 单元测试")
class PricingSuggestionServiceTest {

    private AiPricingSuggestionMapper suggestionMapper;
    private PriceHistoryMapper priceHistoryMapper;
    private ProductMapper productMapper;
    private PricingSuggestionService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant productAssistant = new MapperBuilderAssistant(configuration, "");
        productAssistant.setCurrentNamespace("com.crossborder.pricing.mapper.ProductMapper");
        TableInfoHelper.initTableInfo(productAssistant, Product.class);

        MapperBuilderAssistant suggestionAssistant = new MapperBuilderAssistant(configuration, "");
        suggestionAssistant.setCurrentNamespace("com.crossborder.pricing.mapper.AiPricingSuggestionMapper");
        TableInfoHelper.initTableInfo(suggestionAssistant, AiPricingSuggestion.class);

        MapperBuilderAssistant historyAssistant = new MapperBuilderAssistant(configuration, "");
        historyAssistant.setCurrentNamespace("com.crossborder.pricing.mapper.PriceHistoryMapper");
        TableInfoHelper.initTableInfo(historyAssistant, PriceHistory.class);
    }

    @BeforeEach
    void setUp() {
        suggestionMapper = mock(AiPricingSuggestionMapper.class);
        priceHistoryMapper = mock(PriceHistoryMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new PricingSuggestionServiceImpl(suggestionMapper, priceHistoryMapper, productMapper);
    }

    private Product sampleProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setProductCode("SKU-BLU-001-BLK");
        p.setProductName("无线蓝牙耳机 Pro - 黑色");
        p.setSku("SKU-BLU-001-BLK");
        p.setCostPrice(new BigDecimal("150.00"));
        p.setSellingPrice(new BigDecimal("299.00"));
        return p;
    }

    private AiPricingSuggestion sampleSuggestion() {
        AiPricingSuggestion s = new AiPricingSuggestion();
        s.setId(10L);
        s.setProductId(1L);
        s.setCurrentPrice(new BigDecimal("299.00"));
        s.setSuggestedPrice(new BigDecimal("315.00"));
        s.setConfidenceLevel(new BigDecimal("85.00"));
        s.setReason("竞品均价高于当前售价，建议提价");
        s.setFactors("{\"competitorAvg\": 312.50, \"seasonal\": 1.02}");
        s.setApplied(Boolean.FALSE);
        s.setPricingStrategy("跟随竞品");
        s.setCreateTime(LocalDateTime.now());
        return s;
    }

    // ---------------------------------------------------------------- listTasks

    @Test
    @DisplayName("listTasks - 返回建议列表并带上产品信息")
    void listTasks_returnsSuggestions() {
        when(suggestionMapper.selectList(any())).thenReturn(List.of(sampleSuggestion()));
        when(productMapper.selectById(1L)).thenReturn(sampleProduct());

        List<PricingSuggestionVO> list = service.listSuggestions(50);

        assertEquals(1, list.size());
        PricingSuggestionVO vo = list.get(0);
        assertEquals("SKU-BLU-001-BLK", vo.getSku());
        assertEquals("无线蓝牙耳机 Pro - 黑色", vo.getProductName());
        assertEquals(0, vo.getCurrentPrice().compareTo(new BigDecimal("299.00")));
        assertEquals(0, vo.getSuggestedPrice().compareTo(new BigDecimal("315.00")));
        assertEquals(0, vo.getCompetitorAvg().compareTo(new BigDecimal("312.50")));
        assertEquals("跟随竞品", vo.getStrategy());
        // 预期利润 = 建议价 - 成本价 = 315 - 150 = 165
        assertEquals(0, vo.getExpectedProfit().compareTo(new BigDecimal("165.00")));
    }

    @Test
    @DisplayName("listTasks - 空结果返回空列表而不是 null")
    void listTasks_emptyResult() {
        when(suggestionMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.listSuggestions(50).isEmpty());
    }

    @Test
    @DisplayName("listTasks - limit 被限制在合理范围，SQL 不会带负数")
    void listTasks_clampsLimit() {
        when(suggestionMapper.selectList(any())).thenReturn(List.of());
        service.listSuggestions(-5);
        ArgumentCaptor<Wrapper<AiPricingSuggestion>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(suggestionMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getTargetSql().contains("LIMIT 50"));
    }

    // ---------------------------------------------------------------- suggest

    @Test
    @DisplayName("suggest - 按 SKU 找到产品并返回最新建议")
    void suggest_bySku() {
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        when(suggestionMapper.selectOne(any())).thenReturn(sampleSuggestion());

        PricingSuggestionVO vo = service.getSuggestionBySku("SKU-BLU-001-BLK");

        assertEquals("SKU-BLU-001-BLK", vo.getSku());
        assertEquals(0, vo.getSuggestedPrice().compareTo(new BigDecimal("315.00")));
        assertEquals(Boolean.FALSE, vo.getApplied());
    }

    @Test
    @DisplayName("suggest - SKU 为空抛业务异常")
    void suggest_blankSku() {
        BizException ex = assertThrows(BizException.class, () -> service.getSuggestionBySku(""));
        assertTrue(ex.getMessage().contains("SKU"));
    }

    @Test
    @DisplayName("suggest - 商品不存在抛业务异常而不是返回 null")
    void suggest_productNotFound() {
        when(productMapper.selectOne(any())).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> service.getSuggestionBySku("NOT-EXIST"));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("suggest - 无建议记录时抛异常")
    void suggest_noSuggestion() {
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        when(suggestionMapper.selectOne(any())).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> service.getSuggestionBySku("SKU-BLU-001-BLK"));
        assertTrue(ex.getMessage().contains("暂无定价建议"));
    }

    // ---------------------------------------------------------------- history

    @Test
    @DisplayName("history - 返回价格变更历史并映射成前端视图")
    void history_mapsFields() {
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());

        PriceHistory h = new PriceHistory();
        h.setId(5L);
        h.setProductId(1L);
        h.setProductCode("SKU-BLU-001-BLK");
        h.setOldPrice(new BigDecimal("299.00"));
        h.setNewPrice(new BigDecimal("315.00"));
        h.setAdjustmentReason("竞品涨价");
        h.setAdjustmentType("AUTO");
        h.setPricingStrategy("跟随竞品");
        h.setCompetitorAvgPrice(new BigDecimal("312.50"));
        h.setCreateTime(LocalDateTime.now());
        when(priceHistoryMapper.selectList(any())).thenReturn(List.of(h));

        List<PricingSuggestionVO> list = service.getHistoryBySku("SKU-BLU-001-BLK", 30);

        assertEquals(1, list.size());
        PricingSuggestionVO vo = list.get(0);
        assertEquals("SKU-BLU-001-BLK", vo.getSku());
        assertEquals(0, vo.getCurrentPrice().compareTo(new BigDecimal("299.00")));
        assertEquals(0, vo.getSuggestedPrice().compareTo(new BigDecimal("315.00")));
        assertEquals("竞品涨价", vo.getReason());
        // 历史记录一律视为已生效
        assertEquals(Boolean.TRUE, vo.getApplied());
    }

    @Test
    @DisplayName("history - 没有历史返回空列表")
    void history_empty() {
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        when(priceHistoryMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.getHistoryBySku("SKU-BLU-001-BLK", 30).isEmpty());
    }

    // ---------------------------------------------------------------- apply

    @Test
    @DisplayName("apply - 采纳建议后标记为已应用并写入价格历史")
    void apply_marksAppliedAndWritesHistory() {
        AiPricingSuggestion suggestion = sampleSuggestion();
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        when(suggestionMapper.selectOne(any())).thenReturn(suggestion);

        PricingSuggestionVO vo = service.applySuggestion("SKU-BLU-001-BLK", new BigDecimal("315.00"));

        assertEquals(Boolean.TRUE, vo.getApplied());
        verify(suggestionMapper).updateById(any(AiPricingSuggestion.class));

        ArgumentCaptor<PriceHistory> captor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryMapper).insert(captor.capture());
        PriceHistory saved = captor.getValue();
        assertEquals(1L, saved.getProductId());
        assertEquals(0, saved.getOldPrice().compareTo(new BigDecimal("299.00")));
        assertEquals(0, saved.getNewPrice().compareTo(new BigDecimal("315.00")));
        assertEquals("MANUAL", saved.getAdjustmentType());
    }

    @Test
    @DisplayName("apply - **真的把商品售价改成新价**（原来只改建议+历史 → 假成功）")
    void apply_actuallyUpdatesProductSellingPrice() {
        AiPricingSuggestion suggestion = sampleSuggestion();
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        when(suggestionMapper.selectOne(any())).thenReturn(suggestion);

        service.applySuggestion("SKU-BLU-001-BLK", new BigDecimal("315.00"));

        // 📌 2026-11-11 修：原来 applySuggestion **从不**写 product.selling_price
        //    （全仓 `setSellingPrice` 生产代码 0 处调用）→ 前端弹「已采纳建议价」、
        //    price_history 多一条变更记录，而**商品售价纹丝未动** = 假成功（规则 80/82）
        ArgumentCaptor<Product> pc = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(pc.capture());
        assertEquals(0, new BigDecimal("315.00").compareTo(pc.getValue().getSellingPrice()),
                "采纳建议价必须真的把商品售价改掉");
        assertNotNull(pc.getValue().getUpdateTime());
    }

    @Test
    @DisplayName("apply - 历史记录的旧价取**商品当前售价**，不是建议快照里的过期价")
    void apply_historyOldPrice_comesFromLiveProductPrice() {
        AiPricingSuggestion suggestion = sampleSuggestion();
        suggestion.setCurrentPrice(new BigDecimal("199.00"));   // 建议生成时的旧快照
        Product live = sampleProduct();
        live.setSellingPrice(new BigDecimal("299.00"));          // 商品现在的真实售价
        when(productMapper.selectOne(any())).thenReturn(live);
        when(suggestionMapper.selectOne(any())).thenReturn(suggestion);

        service.applySuggestion("SKU-BLU-001-BLK", new BigDecimal("315.00"));

        ArgumentCaptor<PriceHistory> captor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("299.00").compareTo(captor.getValue().getOldPrice()),
                "旧价必须是真实售价 299，而不是建议快照里的过期价 199");
    }

    @Test
    @DisplayName("apply - 重复采纳被拒绝")
    void apply_rejectsDuplicate() {
        AiPricingSuggestion suggestion = sampleSuggestion();
        suggestion.setApplied(Boolean.TRUE);
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        when(suggestionMapper.selectOne(any())).thenReturn(suggestion);

        BizException ex = assertThrows(BizException.class,
                () -> service.applySuggestion("SKU-BLU-001-BLK", new BigDecimal("315.00")));
        assertTrue(ex.getMessage().contains("已被采纳"));
        verify(suggestionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("apply - 价格为负被拒绝")
    void apply_rejectsNegativePrice() {
        BizException ex = assertThrows(BizException.class,
                () -> service.applySuggestion("SKU-BLU-001-BLK", new BigDecimal("-1")));
        assertTrue(ex.getMessage().contains("价格"));
    }

    @Test
    @DisplayName("apply - 没有建议时不能采纳")
    void apply_requiresSuggestion() {
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        when(suggestionMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.applySuggestion("SKU-BLU-001-BLK", new BigDecimal("315.00")));
        assertTrue(ex.getMessage().contains("暂无定价建议"));
    }

    // ---------------------------------------------------------------- factors 解析

    @Test
    @DisplayName("factors JSON 解析不出竞品均价时返回 null 而不是抛异常")
    void parseCompetitorAvg_robust() {
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        AiPricingSuggestion s = sampleSuggestion();
        s.setFactors("这不是 JSON");
        when(suggestionMapper.selectOne(any())).thenReturn(s);

        PricingSuggestionVO vo = service.getSuggestionBySku("SKU-BLU-001-BLK");
        assertNull(vo.getCompetitorAvg());
        // 其它字段仍应正常
        assertEquals(0, vo.getSuggestedPrice().compareTo(new BigDecimal("315.00")));
    }

    @Test
    @DisplayName("factors 为 null 时竞品均价为 null")
    void parseCompetitorAvg_nullFactors() {
        when(productMapper.selectOne(any())).thenReturn(sampleProduct());
        AiPricingSuggestion s = sampleSuggestion();
        s.setFactors(null);
        when(suggestionMapper.selectOne(any())).thenReturn(s);

        assertNull(service.getSuggestionBySku("SKU-BLU-001-BLK").getCompetitorAvg());
    }
}
