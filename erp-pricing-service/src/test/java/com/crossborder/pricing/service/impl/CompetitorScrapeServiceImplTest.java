package com.crossborder.pricing.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.entity.CompetitorProduct;
import com.crossborder.pricing.entity.Product;
import com.crossborder.pricing.mapper.CompetitorProductMapper;
import com.crossborder.pricing.mapper.ProductMapper;
import com.crossborder.pricing.service.CompetitorScrapeService.CompetitorPriceStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CompetitorScrapeServiceImpl} 单元测试
 *
 * <p>📌 **2026-11-14 重写**。原因：这个实现原先靠 {@code Random} 生成
 * 「竞品价格/评分/评论数」假数据（并提供 {@code PriceRandomizer} 注入点），
 * 现在改成：
 * <ul>
 *   <li>真抓取（OkHttp + Jsoup）—— 网络层在单测里不触发；</li>
 *   <li>真落库（{@code competitor_product} 表）；</li>
 *   <li>查询走 Mapper（不再现场造数）。</li>
 * </ul>
 *
 * <p>📌 **重点回归的缺陷**：均价分母原来用 {@code competitors.size()}（全部条数），
 * 而有 {@code price == null} 的记录被 {@code continue} 跳过 →
 * 均价被算低（3 条里 1 条缺价 → 只有真值的 2/3）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompetitorScrapeServiceImpl 单元测试")
class CompetitorScrapeServiceImplTest {

    @Mock
    private CompetitorProductMapper competitorProductMapper;

    @Mock
    private ProductMapper productMapper;

    private CompetitorScrapeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CompetitorScrapeServiceImpl(competitorProductMapper, productMapper);
    }

    // ========== getCompetitorProducts：查库，不再现场造数 ==========

    @Test
    @DisplayName("getCompetitorProducts - null productId 返回空列表且不查库")
    void getCompetitorProducts_nullProductId_returnsEmptyWithoutQuery() {
        List<CompetitorProduct> competitors = service.getCompetitorProducts(null);

        assertNotNull(competitors);
        assertTrue(competitors.isEmpty());
        verify(competitorProductMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("getCompetitorProducts - 走 Mapper 查询，不再现场生成随机数")
    void getCompetitorProducts_queriesMapper() {
        CompetitorProduct persisted = competitorOf("Amazon 竞品 A", "120.00");
        when(competitorProductMapper.selectList(any())).thenReturn(List.of(persisted));

        List<CompetitorProduct> result = service.getCompetitorProducts(100L);

        assertEquals(1, result.size());
        assertEquals("Amazon 竞品 A", result.get(0).getCompetitorName());
        verify(competitorProductMapper, times(1)).selectList(any());
    }

    // ========== getCompetitorPriceStats：核心算法 ==========

    @Test
    @DisplayName("getCompetitorPriceStats - 3个竞品正确计算 min/max/avg")
    void getCompetitorPriceStats_threeCompetitors_correctStats() {
        when(competitorProductMapper.selectList(any())).thenReturn(List.of(
                competitorOf("A", "120.00"),
                competitorOf("B", "150.00"),
                competitorOf("C", "180.00")));

        CompetitorPriceStats stats = service.getCompetitorPriceStats(100L);

        assertNotNull(stats);
        assertEquals(100L, stats.getProductId());
        assertEquals(0, new BigDecimal("120.00").compareTo(stats.getMinPrice()));
        assertEquals(0, new BigDecimal("180.00").compareTo(stats.getMaxPrice()));
        assertEquals(0, new BigDecimal("150.00").compareTo(stats.getAvgPrice()));
        assertEquals(3L, stats.getCompetitorCount());
    }

    @Test
    @DisplayName("getCompetitorPriceStats - 均价分母用「有效价格条数」，不是全部条数")
    void getCompetitorPriceStats_nullPrice_excludedFromAverageDenominator() {
        // 📌 回归缺陷：原来分母用 competitors.size()=3，但只有 2 条有价
        //    → 均价 = (120+180)/3 = 100（错的）；正确应为 (120+180)/2 = 150
        when(competitorProductMapper.selectList(any())).thenReturn(Arrays.asList(
                competitorOf("A", "120.00"),
                competitorOf("B", null),
                competitorOf("C", "180.00")));

        CompetitorPriceStats stats = service.getCompetitorPriceStats(100L);

        assertNotNull(stats);
        assertEquals(0, new BigDecimal("150.00").compareTo(stats.getAvgPrice()),
                "均价分母必须是有效价格条数（2），不是全部条数（3）");
        assertEquals(2L, stats.getCompetitorCount(),
                "竞品条数应只统计有效价格的记录");
    }

    @Test
    @DisplayName("getCompetitorPriceStats - 全部缺价时返回 null（而不是除零/乱数）")
    void getCompetitorPriceStats_allNullPrices_returnsNull() {
        when(competitorProductMapper.selectList(any())).thenReturn(Arrays.asList(
                competitorOf("A", null),
                competitorOf("B", null)));

        assertNull(service.getCompetitorPriceStats(100L));
    }

    @Test
    @DisplayName("getCompetitorPriceStats - 没有竞品记录时返回 null")
    void getCompetitorPriceStats_noRecords_returnsNull() {
        when(competitorProductMapper.selectList(any())).thenReturn(List.of());

        assertNull(service.getCompetitorPriceStats(100L));
    }

    @Test
    @DisplayName("getCompetitorPriceStats - null productId 返回 null 且不查库")
    void getCompetitorPriceStats_nullProductId_returnsNull() {
        assertNull(service.getCompetitorPriceStats(null));
        verify(competitorProductMapper, never()).selectList(any());
    }

    // ========== addCompetitor：真落库 ==========

    @Test
    @DisplayName("addCompetitor - 真的写库了（原来只打日志就说「添加成功」）")
    void addCompetitor_insertsIntoDatabase() {
        when(productMapper.selectById(100L)).thenReturn(productOf(100L, "PROD-001"));
        when(competitorProductMapper.selectOne(any())).thenReturn(null);

        CompetitorProduct competitor = new CompetitorProduct();
        competitor.setCompetitorName("手动竞品");
        competitor.setCurrentPrice(new BigDecimal("99.00"));
        competitor.setPlatform("Amazon");

        service.addCompetitor(100L, competitor);

        verify(competitorProductMapper, times(1)).insert(any(CompetitorProduct.class));
        assertNotNull(competitor.getCreateTime());
        assertNotNull(competitor.getUpdateTime());
        assertEquals(100L, competitor.getProductId());
    }

    @Test
    @DisplayName("addCompetitor - 商品不存在时抛异常，不静默")
    void addCompetitor_productNotFound_throws() {
        when(productMapper.selectById(anyLong())).thenReturn(null);

        CompetitorProduct competitor = new CompetitorProduct();
        competitor.setCompetitorName("X");
        competitor.setCurrentPrice(new BigDecimal("1.00"));

        assertThrows(BizException.class, () -> service.addCompetitor(404L, competitor));
        verify(competitorProductMapper, never()).insert(any());
    }

    @Test
    @DisplayName("addCompetitor - 价格为空时抛异常（DDL 里 price 是 NOT NULL）")
    void addCompetitor_nullPrice_throws() {
        when(productMapper.selectById(100L)).thenReturn(productOf(100L, "PROD-001"));
        // 📌 不再 stub selectOne：现在实现是**先校验价格再落库**（早失败），
        //    所以 upsert 根本不会被调用 → 这条 stub 会变成 UnnecessaryStubbing。

        CompetitorProduct competitor = new CompetitorProduct();
        competitor.setCompetitorName("X");

        assertThrows(BizException.class, () -> service.addCompetitor(100L, competitor));
        verify(competitorProductMapper, never()).insert(any());
    }

    @Test
    @DisplayName("addCompetitor - 同名竞品已存在时走更新而不是插入")
    void addCompetitor_existingCompetitor_updatesInsteadOfInsert() {
        when(productMapper.selectById(100L)).thenReturn(productOf(100L, "PROD-001"));
        CompetitorProduct existing = competitorOf("手动竞品", "80.00");
        existing.setId(7L);
        when(competitorProductMapper.selectOne(any())).thenReturn(existing);

        CompetitorProduct competitor = new CompetitorProduct();
        competitor.setCompetitorName("手动竞品");
        competitor.setCurrentPrice(new BigDecimal("99.00"));
        competitor.setPlatform("Amazon");

        service.addCompetitor(100L, competitor);

        verify(competitorProductMapper, never()).insert(any());
        verify(competitorProductMapper, times(1)).updateById(any(CompetitorProduct.class));
        assertEquals(7L, competitor.getId());
    }

    // ========== 抓取：参数校验 ==========

    @Test
    @DisplayName("scrapeFromPlatform - URL 为空时抛异常（原来静默吞掉）")
    void scrapeFromPlatform_emptyUrl_throws() {
        assertThrows(BizException.class, () -> service.scrapeFromPlatform("Amazon", ""));
        assertThrows(BizException.class, () -> service.scrapeFromPlatform("Amazon", null));
    }

    @Test
    @DisplayName("scrapeFromPlatform - 平台为空时抛异常")
    void scrapeFromPlatform_emptyPlatform_throws() {
        assertThrows(BizException.class, () -> service.scrapeFromPlatform("", "https://x.com/a"));
        assertThrows(BizException.class, () -> service.scrapeFromPlatform(null, "https://x.com/a"));
    }

    @Test
    @DisplayName("scrapeCompetitorProducts - productId 为 null 时抛异常")
    void scrapeCompetitorProducts_nullProductId_throws() {
        assertThrows(BizException.class, () -> service.scrapeCompetitorProducts(null));
    }

    @Test
    @DisplayName("scrapeCompetitorProducts - 商品不存在时抛异常")
    void scrapeCompetitorProducts_productNotFound_throws() {
        when(productMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BizException.class, () -> service.scrapeCompetitorProducts(404L));
    }

    // ========== batchScrape ==========

    @Test
    @DisplayName("batchScrape - 空集合直接返回，不抛异常")
    void batchScrape_emptyList_returnsQuietly() {
        service.batchScrape(List.of());
        service.batchScrape(null);
        verify(productMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("batchScrape - 单个产品失败不影响整个批次")
    void batchScrape_oneFailsOthersContinue() {
        when(productMapper.selectById(anyLong())).thenReturn(productOf(1L, "PROD-001"));
        when(competitorProductMapper.selectOne(any())).thenReturn(null);

        // 📌 **规则 211**：单测**不得**真连外网。
        //    原实现会真的去打 amazon/eBay（callTimeout 30s）→ 这个用例单跑 30.355s，
        //    而且结果取决于外网可达性 = 单测不确定。
        //    ✅ 用 spy 把「抓取」这一层替换掉，**生产代码一行不改**：
        //       第 1 次调用返回一条竞品（→ 落库 1 条），之后全部抛异常（→ 该产品全平台失败）。
        CompetitorScrapeServiceImpl spyService = spy(service);
        doReturn(competitorOf("Amazon竞品", "88.00"))
                .doThrow(new BizException("模拟平台不可达"))
                .when(spyService).scrapeFromPlatform(anyString(), anyString());

        // 产品 1：4 个平台里第 1 个成功 → saved=1 → 正常返回
        // 产品 2/3：全部平台失败 → 抛 BizException → batchScrape 记账后**继续下一个**
        spyService.batchScrape(List.of(1L, 2L, 3L));

        // 只有产品 1 的那次成功真的落了库（1 条）
        verify(competitorProductMapper, times(1)).insert(any(CompetitorProduct.class));
    }

    // ========== 辅助方法 ==========

    private static CompetitorProduct competitorOf(String name, String price) {
        CompetitorProduct c = new CompetitorProduct();
        c.setCompetitorName(name);
        c.setPlatform("Amazon");
        if (price != null) {
            c.setCurrentPrice(new BigDecimal(price));
        }
        c.setScrapeTime(java.time.LocalDateTime.now());
        c.setCreateTime(java.time.LocalDateTime.now());
        c.setUpdateTime(java.time.LocalDateTime.now());
        return c;
    }

    private static Product productOf(Long id, String code) {
        Product p = new Product();
        p.setId(id);
        p.setProductCode(code);
        p.setProductName("测试商品");
        p.setCostPrice(new BigDecimal("50.00"));
        p.setSellingPrice(new BigDecimal("100.00"));
        return p;
    }
}
