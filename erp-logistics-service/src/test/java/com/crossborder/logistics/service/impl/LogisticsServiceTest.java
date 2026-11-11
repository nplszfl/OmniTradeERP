package com.crossborder.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.logistics.dto.LogisticsChannelVO;
import com.crossborder.logistics.dto.LogisticsRateVO;
import com.crossborder.logistics.dto.ShippingRateQuery;
import com.crossborder.logistics.dto.ShippingRateResult;
import com.crossborder.logistics.entity.LogisticsChannel;
import com.crossborder.logistics.entity.LogisticsRate;
import com.crossborder.logistics.mapper.LogisticsChannelMapper;
import com.crossborder.logistics.mapper.LogisticsRateMapper;
import com.crossborder.logistics.service.LogisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LogisticsService 单元测试
 *
 * <p>覆盖前端 erp-web/src/views/logistics/Logistics.vue 依赖的核心逻辑：
 * 渠道列表、运费计算、渠道推荐、费率 CRUD。</p>
 */
@DisplayName("LogisticsService 单元测试")
class LogisticsServiceTest {

    private LogisticsChannelMapper channelMapper;
    private LogisticsRateMapper rateMapper;
    private LogisticsService service;

    @BeforeEach
    void setUp() {
        channelMapper = mock(LogisticsChannelMapper.class);
        rateMapper = mock(LogisticsRateMapper.class);
        service = new LogisticsServiceImpl(channelMapper, rateMapper);
    }

    private LogisticsRate rate(String code, double min, Double max, double base, double perKg, int days) {
        LogisticsRate r = new LogisticsRate();
        r.setId((long) code.hashCode());
        r.setChannelCode(code);
        r.setChannelName(code + "渠道");
        r.setCarrier(code);
        r.setFromCountry("CN");
        r.setToCountry("US");
        r.setWeightRangeMin(BigDecimal.valueOf(min));
        r.setWeightRangeMax(max == null ? null : BigDecimal.valueOf(max));
        r.setBasePrice(BigDecimal.valueOf(base));
        r.setPricePerKg(BigDecimal.valueOf(perKg));
        r.setEstimatedDays(days);
        r.setCurrency("USD");
        r.setIsActive(Boolean.TRUE);
        return r;
    }

    private ShippingRateQuery query(double weight) {
        ShippingRateQuery q = new ShippingRateQuery();
        q.setFromCountry("CN");
        q.setToCountry("US");
        q.setWeight(BigDecimal.valueOf(weight));
        return q;
    }

    // ---------------------------------------------------------------- 渠道

    @Test
    @DisplayName("listChannels - 映射 JSON 数组字段为前端要的 string[]")
    void listChannels_parsesJsonArrays() {
        LogisticsChannel c = new LogisticsChannel();
        c.setId(1L);
        c.setChannelCode("DHL_EXPRESS");
        c.setChannelName("DHL国际快递");
        c.setCarrier("DHL");
        c.setType("EXPRESS");
        c.setCoverage("[\"CN\",\"US\"]");
        c.setFeatures("[\"门到门\",\"可追踪\"]");
        c.setAvgDeliveryDays(3);
        c.setTrackingSupport(Boolean.TRUE);
        c.setEnabled(Boolean.TRUE);
        when(channelMapper.selectList(any())).thenReturn(List.of(c));

        List<LogisticsChannelVO> list = service.listChannels();

        assertEquals(1, list.size());
        LogisticsChannelVO vo = list.get(0);
        assertEquals("DHL_EXPRESS", vo.getCode());
        assertEquals("DHL国际快递", vo.getName());
        assertEquals(List.of("CN", "US"), vo.getCoverage());
        assertEquals(List.of("门到门", "可追踪"), vo.getFeatures());
        assertEquals(3, vo.getAvgDeliveryDays());
    }

    @Test
    @DisplayName("listChannels - coverage 为 null 时返回空列表而不是 NPE")
    void listChannels_nullCoverage() {
        LogisticsChannel c = new LogisticsChannel();
        c.setChannelCode("X");
        when(channelMapper.selectList(any())).thenReturn(List.of(c));

        LogisticsChannelVO vo = service.listChannels().get(0);
        assertNotNull(vo.getCoverage());
        assertTrue(vo.getCoverage().isEmpty());
    }

    // ---------------------------------------------------------------- 运费计算

    @Test
    @DisplayName("calculateRates - 总价 = 基础价 + (计费重 - 下限) × 每公斤加价")
    void calculateRates_computesTotal() {
        // 重量 2kg，落在 [1, 5) 区间：基础 30 + (2-1)*4.5 = 34.5
        when(rateMapper.selectList(any())).thenReturn(List.of(
                rate("DHL", 1.0, 5.0, 30.0, 4.5, 4)));

        List<ShippingRateResult> list = service.calculateRates(query(2.0));

        assertEquals(1, list.size());
        assertEquals(0, list.get(0).getTotalPrice().compareTo(new BigDecimal("34.50")));
        assertEquals("DHL渠道", list.get(0).getChannelName());
        assertEquals("USD", list.get(0).getCurrency());
        assertEquals("4-7天", list.get(0).getTransitTime());
    }

    @Test
    @DisplayName("calculateRates - 重量上限为 null 时不限重")
    void calculateRates_nullMaxWeight() {
        when(rateMapper.selectList(any())).thenReturn(List.of(
                rate("YANWEN", 2.0, null, 19.4, 2.6, 14)));

        List<ShippingRateResult> list = service.calculateRates(query(10.0));
        assertEquals(1, list.size());
        // 19.4 + (10-2)*2.6 = 40.2
        assertEquals(0, list.get(0).getTotalPrice().compareTo(new BigDecimal("40.20")));
    }

    @Test
    @DisplayName("calculateRates - 超出重量上限的费率被排除")
    void calculateRates_excludesOverweight() {
        when(rateMapper.selectList(any())).thenReturn(List.of(
                rate("LIGHT", 0.0, 1.0, 20.0, 5.0, 3),
                rate("HEAVY", 0.0, null, 50.0, 1.0, 10)));

        List<ShippingRateResult> list = service.calculateRates(query(5.0));
        assertEquals(1, list.size());
        assertEquals("HEAVY", list.get(0).getChannelCode());
    }

    @Test
    @DisplayName("calculateRates - 按总价升序返回")
    void calculateRates_sortedByPrice() {
        when(rateMapper.selectList(any())).thenReturn(List.of(
                rate("EXPENSIVE", 0.0, null, 80.0, 0.0, 3),
                rate("CHEAP", 0.0, null, 10.0, 0.0, 15)));

        List<ShippingRateResult> list = service.calculateRates(query(1.0));
        assertEquals("CHEAP", list.get(0).getChannelCode());
    }

    @Test
    @DisplayName("calculateRates - 体积重大于实重时按体积重计费")
    void calculateRates_volumetricWeight() {
        // 30×20×15 = 9000 cm³ ÷ 6000 = 1.5kg 体积重 > 1kg 实重
        ShippingRateQuery q = query(1.0);
        q.setLength(new BigDecimal("30"));
        q.setWidth(new BigDecimal("20"));
        q.setHeight(new BigDecimal("15"));

        when(rateMapper.selectList(any())).thenReturn(List.of(
                rate("DHL", 0.0, null, 20.0, 10.0, 3)));

        List<ShippingRateResult> list = service.calculateRates(q);
        // 20 + 1.5*10 = 35
        assertEquals(0, list.get(0).getTotalPrice().compareTo(new BigDecimal("35.00")));
    }

    @Test
    @DisplayName("calculateRates - 目的国为空抛业务异常")
    void calculateRates_blankToCountry() {
        ShippingRateQuery q = new ShippingRateQuery();
        q.setWeight(BigDecimal.ONE);
        BizException ex = assertThrows(BizException.class, () -> service.calculateRates(q));
        assertTrue(ex.getMessage().contains("目的国"));
    }

    @Test
    @DisplayName("calculateRates - 重量为 0 抛业务异常")
    void calculateRates_zeroWeight() {
        BizException ex = assertThrows(BizException.class, () -> service.calculateRates(query(0)));
        assertTrue(ex.getMessage().contains("重量"));
    }

    @Test
    @DisplayName("calculateRates - 无匹配渠道返回空列表")
    void calculateRates_noMatch() {
        when(rateMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.calculateRates(query(1.0)).isEmpty());
    }

    // ---------------------------------------------------------------- 推荐

    @Test
    @DisplayName("recommendChannel - 返回推荐标记的那条")
    void recommendChannel_returnsRecommended() {
        when(rateMapper.selectList(any())).thenReturn(List.of(
                rate("FAST", 0.0, null, 30.0, 0.0, 3),
                rate("CHEAP", 0.0, null, 10.0, 0.0, 20)));

        ShippingRateResult r = service.recommendChannel(query(1.0));
        assertNotNull(r);
        assertEquals(Boolean.TRUE, r.getIsRecommended());
    }

    @Test
    @DisplayName("recommendChannel - 无匹配渠道时抛异常")
    void recommendChannel_noMatch() {
        when(rateMapper.selectList(any())).thenReturn(List.of());
        BizException ex = assertThrows(BizException.class, () -> service.recommendChannel(query(1.0)));
        assertTrue(ex.getMessage().contains("没有匹配"));
    }

    // ---------------------------------------------------------------- 费率 CRUD

    @Test
    @DisplayName("createRate - 落库前补默认值")
    void createRate_fillsDefaults() {
        when(rateMapper.insert(any())).thenAnswer(inv -> {
            LogisticsRate r = inv.getArgument(0);
            r.setId(99L);
            return 1;
        });

        LogisticsRateVO vo = new LogisticsRateVO();
        vo.setChannelCode("DHL_EXPRESS");
        vo.setBasePrice(new BigDecimal("22.00"));

        Long id = service.createRate(vo);
        assertEquals(99L, id);

        var captor = org.mockito.ArgumentCaptor.forClass(LogisticsRate.class);
        verify(rateMapper).insert(captor.capture());
        LogisticsRate saved = captor.getValue();
        assertEquals("USD", saved.getCurrency());
        assertEquals(Boolean.TRUE, saved.getIsActive());
        assertEquals(0, saved.getWeightRangeMin().compareTo(BigDecimal.ZERO));
        assertEquals(0, saved.getPricePerKg().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("createRate - 渠道编码为空抛异常")
    void createRate_blankChannelCode() {
        LogisticsRateVO vo = new LogisticsRateVO();
        vo.setBasePrice(BigDecimal.ONE);
        BizException ex = assertThrows(BizException.class, () -> service.createRate(vo));
        assertTrue(ex.getMessage().contains("渠道编码"));
    }

    @Test
    @DisplayName("updateRate - 只覆盖传了的字段，null 字段保持原值")
    void updateRate_partialUpdate() {
        LogisticsRate existing = rate("DHL", 0.0, null, 20.0, 5.0, 3);
        when(rateMapper.selectById(1L)).thenReturn(existing);

        LogisticsRateVO vo = new LogisticsRateVO();
        vo.setBasePrice(new BigDecimal("99.00"));
        service.updateRate(1L, vo);

        var captor = org.mockito.ArgumentCaptor.forClass(LogisticsRate.class);
        verify(rateMapper).updateById(captor.capture());
        LogisticsRate saved = captor.getValue();
        assertEquals(0, saved.getBasePrice().compareTo(new BigDecimal("99.00")));
        // 没传的字段保持原值
        assertEquals("DHL", saved.getChannelCode());
        assertEquals(3, saved.getEstimatedDays());
    }

    @Test
    @DisplayName("updateRate - 费率不存在抛异常")
    void updateRate_notFound() {
        when(rateMapper.selectById(any())).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> service.updateRate(1L, new LogisticsRateVO()));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("deleteRate - 费率不存在抛异常")
    void deleteRate_notFound() {
        when(rateMapper.selectById(any())).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.deleteRate(1L));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("deleteRate - 存在时删除")
    void deleteRate_ok() {
        when(rateMapper.selectById(1L)).thenReturn(rate("DHL", 0.0, null, 20.0, 5.0, 3));
        service.deleteRate(1L);
        verify(rateMapper).deleteById(1L);
    }

    // ========== createChannel（2026-11-11 新增） ==========
    // 📌 背景：前端「添加渠道」原本只弹「渠道添加成功」，**后端连端点都没有** → 假成功（规则 80 / 82）

    @Test
    @DisplayName("createChannel - 真的落库，并把默认值补齐")
    void createChannel_fillsDefaults() {
        when(channelMapper.selectCount(any())).thenReturn(0L);
        when(channelMapper.insert(any())).thenAnswer(inv -> {
            LogisticsChannel c = inv.getArgument(0);
            c.setId(777L);
            return 1;
        });

        LogisticsChannelVO vo = new LogisticsChannelVO();
        vo.setCode("  SF_EXPRESS  ");
        vo.setName("顺丰国际");
        vo.setCarrier("SF");
        vo.setCoverage(List.of("CN", "US"));

        Long id = service.createChannel(vo);
        assertEquals(777L, id);

        var captor = org.mockito.ArgumentCaptor.forClass(LogisticsChannel.class);
        verify(channelMapper).insert(captor.capture());
        LogisticsChannel saved = captor.getValue();
        assertEquals("SF_EXPRESS", saved.getChannelCode());   // 编码去空格
        assertEquals("EXPRESS", saved.getType());              // 默认类型
        assertEquals(Boolean.TRUE, saved.getEnabled());        // 📌 不启用的话 listChannels() 根本看不见
        assertEquals(7, saved.getAvgDeliveryDays());
        assertEquals("[\"CN\",\"US\"]", saved.getCoverage());   // 列表 → JSON 数组字符串
        assertEquals("[]", saved.getFeatures());
    }

    @Test
    @DisplayName("createChannel - 渠道编码已存在抛业务异常（而不是让 DB 报唯一索引冲突）")
    void createChannel_duplicateCode() {
        when(channelMapper.selectCount(any())).thenReturn(1L);
        LogisticsChannelVO vo = new LogisticsChannelVO();
        vo.setCode("DHL_EXPRESS");
        vo.setName("DHL国际快递");
        vo.setCarrier("DHL");

        BizException ex = assertThrows(BizException.class, () -> service.createChannel(vo));
        assertTrue(ex.getMessage().contains("已存在"));
        verify(channelMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createChannel - 编码/名称/承运商为空抛业务异常")
    void createChannel_blankFields() {
        LogisticsChannelVO blank = new LogisticsChannelVO();
        assertThrows(BizException.class, () -> service.createChannel(blank));

        LogisticsChannelVO noName = new LogisticsChannelVO();
        noName.setCode("X");
        assertThrows(BizException.class, () -> service.createChannel(noName));

        LogisticsChannelVO noCarrier = new LogisticsChannelVO();
        noCarrier.setCode("X");
        noCarrier.setName("Y");
        assertThrows(BizException.class, () -> service.createChannel(noCarrier));
    }
}
