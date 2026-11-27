package com.crossborder.productdescription.service.impl;

import com.crossborder.productdescription.entity.DescriptionTemplate;
import com.crossborder.productdescription.mapper.DescriptionTemplateMapper;
import com.crossborder.productdescription.service.DescriptionTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 「描述模板服务」单元测试（Mockito mock Mapper —— src/test 才允许 mock）。
 *
 * <p>🔴 <b>守护的三个真缺陷</b>：
 * <ol>
 *   <li><b>大小写</b>：生产种子种的是 {@code Amazon}/{@code eBay}/{@code Shopee}
 *       （首字母大写），而请求传的是 {@code amazon}/{@code ebay}/{@code shopee}
 *       （全小写）→ 原来靠 MySQL 的 {@code utf8mb4_unicode_ci}（大小写不敏感）
 *       才**碰巧**匹配上 → 依赖「排序规则恰好不敏感」这一隐式前提：
 *       H2 测试库（默认 CASE_SENSITIVE）匹配不上；哪天 collation 改成
 *       {@code utf8mb4_bin} 线上直接匹配不上。</li>
 *   <li><b>串台</b>：请求 {@code targetPlatform=amazon} 但库里没有 amazon 模板时，
 *       旧逻辑会返回**别的平台**的模板（比如 shopee 的）→ 明明要 Amazon 格式，
 *       却按 Shopee 的模板写文案，且 {@code templateId} 记的是
 *       {@code shopee_template} → <b>追溯时完全看不出「用错模板了」</b>。</li>
 *   <li><b>selectOne 抛异常</b>：{@code template_id} 上有 UNIQUE 约束，但 MySQL 的
 *       {@code utf8mb4_unicode_ci} 是大小写不敏感的 → {@code Amazon_Template} 与
 *       {@code amazon_template} 在 UNIQUE 索引里是**同一个键** → 真出现两条只差
 *       大小写的行时，{@code selectOne} 会抛 TooManyResultsException。</li>
 * </ol>
 */
@DisplayName("描述模板服务（大小写 / 串台 / 唯一键）")
class DescriptionTemplateServiceImplTest {

    private DescriptionTemplateMapper mapper;
    private DescriptionTemplateService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DescriptionTemplateMapper.class);
        service = new DescriptionTemplateServiceImpl(mapper);
    }

    private DescriptionTemplate row(Long id, String templateId, String platform, String language) {
        DescriptionTemplate t = new DescriptionTemplate();
        t.setId(id);
        t.setTemplateId(templateId);
        t.setName(templateId);
        t.setPlatform(platform);
        t.setLanguage(language);
        t.setTemplateContent("正文 " + templateId);
        t.setVariables("[\"产品名称\"]");
        t.setMaxLength(2000);
        t.setEnabled(1);
        return t;
    }

    @Test
    @DisplayName("平台名大小写不一致也要匹配上（生产种的是 Amazon，请求传 amazon）")
    void platformMatchIsCaseInsensitive() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row(1L, "amazon_template", "Amazon", "zh"),
                row(2L, "ebay_template", "eBay", "zh"),
                row(3L, "shopee_template", "Shopee", "zh"),
                row(4L, "general_template", "general", "zh")));

        List<DescriptionTemplate> rows = service.listEnabled("amazon", "zh");

        assertEquals(1, rows.size(), "应只匹配到 amazon 那一条，实际=" + rows.size());
        assertEquals("amazon_template", rows.get(0).getTemplateId());
    }

    @Test
    @DisplayName("指定了平台却匹配不上 → 返回空，绝不返回别的平台的模板（不串台）")
    void noCrossPlatformFallback() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row(2L, "ebay_template", "eBay", "zh"),
                row(3L, "shopee_template", "Shopee", "zh")));

        Optional<DescriptionTemplate> found = service.findDefault("amazon", "zh");

        assertTrue(found.isEmpty(),
                "没有 amazon 模板时必须返回空（而不是悄悄用 shopee 的），实际=" + found);
    }

    @Test
    @DisplayName("平台 + 语言都匹配 → 优先用它")
    void exactPlatformAndLanguageWins() {
        // 📌 这两个模板 ID 是**测试自己造的**（生产种的是 amazon_template 等），
        //    用来验证「平台+语言优先」这条规则 → 显式豁免（dangling-ref:allow）。
        when(mapper.selectList(any())).thenReturn(List.of(
                row(1L, "amazon_zh_template", "Amazon", "zh"), // dangling-ref:allow
                row(2L, "amazon_en_template", "Amazon", "en"))); // dangling-ref:allow

        Optional<DescriptionTemplate> found = service.findDefault("amazon", "en");

        assertTrue(found.isPresent());
        assertEquals("amazon_en_template", found.get().getTemplateId()); // dangling-ref:allow
    }

    @Test
    @DisplayName("按 template_id 取模板 → 大小写不敏感（不能因 UNIQUE 索引大小写不敏感而抛异常）")
    void findByTemplateIdIsCaseInsensitive() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row(1L, "amazon_template", "Amazon", "zh")));

        assertTrue(service.findByTemplateId("amazon_template").isPresent());
        assertTrue(service.findByTemplateId("AMAZON_TEMPLATE").isPresent(),
                "大小写不同的模板 ID 也必须能取到");
        assertTrue(service.findByTemplateId("Amazon_Template").isPresent());
    }

    @Test
    @DisplayName("库里没有该模板 → 返回空（不编一个模板出来）")
    void findByTemplateIdMissingReturnsEmpty() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row(1L, "amazon_template", "Amazon", "zh")));

        // 📌 `nonexistent_template` 是**测试故意造的**不存在的 ID
        //    （验证「取不到 → 返回空，不编」）→ 显式豁免（dangling-ref:allow）。
        assertTrue(service.findByTemplateId("nonexistent_template").isEmpty()); // dangling-ref:allow
        assertTrue(service.findByTemplateId(null).isEmpty());
        assertTrue(service.findByTemplateId("  ").isEmpty());
    }

    @Test
    @DisplayName("只查启用中的模板（enabled=1）—— 停用的模板不该被用")
    void onlyEnabledTemplatesAreListed() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row(1L, "amazon_template", "Amazon", "zh")));

        service.listEnabled(null, null);

        // 📌 enabled 条件必须下推到 SQL（而不是拉回内存再筛）
        //    → 停用的模板根本不该被查出来
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<DescriptionTemplate>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(captor.capture());
        assertNotNull(captor.getValue());
        String sql = captor.getValue().getTargetSql();
        assertTrue(sql.contains("enabled"), "查询条件里必须带 enabled，实际=" + sql);
    }

    @Test
    @DisplayName("模板表查不到任何行 → 返回空列表，不抛异常")
    void emptyTableReturnsEmptyList() {
        when(mapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.listEnabled(null, null).isEmpty());
        assertTrue(service.findByTemplateId("amazon_template").isEmpty());
    }
}
