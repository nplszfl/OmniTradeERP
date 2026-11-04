package com.crossborder.productdescription.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveResponse;
import com.crossborder.productdescription.service.ProductDescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 产品描述<b>真实落库</b>测试（H2 内存库 + 真实 Mapper）
 *
 * <p>🔴 <b>这个测试守护的是什么缺陷</b>：
 * <pre>
 * saveDescription()   → log.info + return true          ← 什么都没写
 * getProductDescriptions() → 返回一条写死的假数据        ← 根本没查库
 * </pre>
 * 单测（Mockito）完全测不出来：mock 掉 Mapper 后，
 * 「有没有真的写进去」根本不在断言范围内。</p>
 *
 * <p>📌 <b>所以这里的断言方式是查库</b>（{@code JdbcTemplate} 直接数行数），
 * 而不是只看方法返回值 —— 本项目的教训：
 * <b>「断言返回值 true」≠「数据落库了」</b>。</p>
 */
@SpringBootTest
@DisplayName("产品描述持久化（真实落库）")
class ProductDescriptionPersistenceTest {

    /** 雪花 ID：19 位，JS 的 Number 装不下 → 前端必须用字符串承载 */
    private static final long PRODUCT_ID = 2117546519450398722L;

    private static final String SKU = "SKU-TEST-0001";

    @Autowired
    private ProductDescriptionService productDescriptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product_description");
        jdbcTemplate.update("DELETE FROM erp_product.t_product_sku");
        jdbcTemplate.update("DELETE FROM erp_product.t_product");
        jdbcTemplate.update(
                "INSERT INTO erp_product.t_product (id, internal_sku, product_name, status, deleted) "
                        + "VALUES (?, ?, ?, 1, 0)",
                PRODUCT_ID, SKU, "无线蓝牙耳机");
    }

    /**
     * 插一条<b>变体 SKU</b>（t_product_sku.sku_code）
     *
     * 📌 前端「AI 商品描述」页展示的 SKU 就是这一种（带颜色/规格后缀），
     *    商品列表页展示的却是商品级 SKU（internal_sku）——
     *    <b>两种都要能解析</b>，否则「明明有这个商品却存不进去」。
     */
    private void insertVariantSku(String skuCode, long productId) {
        jdbcTemplate.update(
                "INSERT INTO erp_product.t_product_sku "
                        + "(id, product_id, sku_code, sku_name, status, deleted) "
                        + "VALUES (?, ?, ?, ?, 1, 0)",
                skuCode.hashCode() & 0x7fffffff, productId, skuCode, skuCode + "-变体");
    }

    // ------------------------------------------------------------ 内部保存接口

    @Test
    @DisplayName("saveDescription 必须真的写进库（不是只打个日志）")
    void saveDescription_WritesRowIntoDatabase() {
        DescriptionGenerationResponse response = new DescriptionGenerationResponse();
        response.setProductId(PRODUCT_ID);
        response.setProductName("无线蓝牙耳机");
        response.setContent("主动降噪，30 小时续航。");
        response.setLanguage("zh");
        response.setPlatform("amazon");
        response.setShortTitle("蓝牙耳机");

        assertTrue(productDescriptionService.saveDescription(response));

        // 📌 核心断言：**库里真的有一行**
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_description WHERE product_id = ?",
                Integer.class, PRODUCT_ID);
        assertEquals(1, count, "保存后数据库里必须真的有一行");

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM product_description WHERE product_id = ?",
                String.class, PRODUCT_ID);
        assertEquals("主动降噪，30 小时续航。", content, "内容必须原样落库");

        Integer version = jdbcTemplate.queryForObject(
                "SELECT version_number FROM product_description WHERE product_id = ?",
                Integer.class, PRODUCT_ID);
        assertEquals(1, version, "首次保存版本号应为 1");
    }

    @Test
    @DisplayName("同一 (商品, 语言, 平台) 重复保存 → 还是一行，但版本号递增")
    void saveDescription_Repeated_SameRowWithIncreasingVersion() {
        DescriptionGenerationResponse first = new DescriptionGenerationResponse();
        first.setProductId(PRODUCT_ID);
        first.setProductName("无线蓝牙耳机");
        first.setContent("第一版");
        first.setLanguage("zh");
        first.setPlatform("amazon");
        productDescriptionService.saveDescription(first);

        DescriptionGenerationResponse second = new DescriptionGenerationResponse();
        second.setProductId(PRODUCT_ID);
        second.setProductName("无线蓝牙耳机");
        second.setContent("第二版");
        second.setLanguage("zh");
        second.setPlatform("amazon");
        productDescriptionService.saveDescription(second);

        // 📌 自然唯一键 uk_pd_product_lang_platform → 只能有一行
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_description WHERE product_id = ?",
                Integer.class, PRODUCT_ID);
        assertEquals(1, count, "唯一键约束下重复保存只能有一行");

        Integer version = jdbcTemplate.queryForObject(
                "SELECT version_number FROM product_description WHERE product_id = ?",
                Integer.class, PRODUCT_ID);
        assertEquals(2, version, "重复保存时版本号必须递增");

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM product_description WHERE product_id = ?",
                String.class, PRODUCT_ID);
        assertEquals("第二版", content, "重复保存必须更新内容");
    }

    @Test
    @DisplayName("不同平台各存一行（唯一键是 商品+语言+平台）")
    void saveDescription_DifferentPlatform_SeparateRows() {
        DescriptionGenerationResponse amazon = new DescriptionGenerationResponse();
        amazon.setProductId(PRODUCT_ID);
        amazon.setProductName("无线蓝牙耳机");
        amazon.setContent("Amazon 版");
        amazon.setLanguage("zh");
        amazon.setPlatform("amazon");
        productDescriptionService.saveDescription(amazon);

        DescriptionGenerationResponse ebay = new DescriptionGenerationResponse();
        ebay.setProductId(PRODUCT_ID);
        ebay.setProductName("无线蓝牙耳机");
        ebay.setContent("eBay 版");
        ebay.setLanguage("zh");
        ebay.setPlatform("ebay");
        productDescriptionService.saveDescription(ebay);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_description WHERE product_id = ?",
                Integer.class, PRODUCT_ID);
        assertEquals(2, count, "不同平台应该是两行");
    }

    @Test
    @DisplayName("productId 为空 → 抛 BizException，且库里一行都不许多")
    void saveDescription_NullProductId_ThrowsAndWritesNothing() {
        DescriptionGenerationResponse response = new DescriptionGenerationResponse();
        response.setProductName("无线蓝牙耳机");
        response.setContent("内容");

        BizException e = assertThrows(BizException.class,
                () -> productDescriptionService.saveDescription(response));
        assertTrue(e.getMessage().contains("产品ID"), e.getMessage());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_description", Integer.class);
        assertEquals(0, count, "失败时不能写进任何一行");
    }

    // ------------------------------------------------------------ 查询接口

    @Test
    @DisplayName("getProductDescriptions 必须查库（查得到就是真数据）")
    void getProductDescriptions_ReturnsPersistedRows() {
        DescriptionGenerationResponse response = new DescriptionGenerationResponse();
        response.setProductId(PRODUCT_ID);
        response.setProductName("无线蓝牙耳机");
        response.setContent("落库的正文");
        response.setLanguage("zh");
        response.setPlatform("amazon");
        productDescriptionService.saveDescription(response);

        List<DescriptionGenerationResponse> descriptions =
                productDescriptionService.getProductDescriptions(PRODUCT_ID);

        assertNotNull(descriptions);
        assertEquals(1, descriptions.size());
        assertEquals("落库的正文", descriptions.get(0).getContent());
        assertEquals("zh", descriptions.get(0).getLanguage());
        assertEquals("amazon", descriptions.get(0).getPlatform());
        assertEquals(PRODUCT_ID, descriptions.get(0).getProductId());
    }

    @Test
    @DisplayName("查不到时返回空列表，绝不返回写死的假数据")
    void getProductDescriptions_NoRows_ReturnsEmptyList() {
        List<DescriptionGenerationResponse> descriptions =
                productDescriptionService.getProductDescriptions(999999L);

        assertNotNull(descriptions);
        assertTrue(descriptions.isEmpty(),
                "库里没有记录时必须返回空列表，不能再返回那条写死的假描述");
    }

    // ------------------------------------------------------------ 前端契约保存接口

    @Test
    @DisplayName("前端契约保存：SKU → productId 解析 + 真落库 + productId 用字符串")
    void saveFrontendDescription_ResolvesSkuAndPersists() {
        FrontendDescriptionSaveRequest request = new FrontendDescriptionSaveRequest();
        request.setSku(SKU);
        request.setTitle("无线蓝牙耳机");
        request.setSubtitle("主动降噪");
        request.setBody("Hi-Fi 音质，30 小时续航。");
        request.setBulletPoints(List.of("主动降噪", "30 小时续航"));
        request.setLanguage("zh");
        request.setPlatform("amazon");
        request.setTemplateId("amazon_product_template");
        request.setGenerationTime(1234L);

        FrontendDescriptionSaveResponse response =
                productDescriptionService.saveFrontendDescription(request);

        assertNotNull(response);
        assertTrue(response.getSaved());
        assertEquals(1, response.getVersionNumber());

        // 📌 雪花 ID 必须用字符串承载：JS 的 Number 只能精确到 2^53-1（16 位），
        //    19 位数字用数字返回必然丢精度（前端回传后匹配不到任何行 → 静默失败）。
        assertNotNull(response.getProductId());
        assertEquals(String.valueOf(PRODUCT_ID), response.getProductId());
        assertEquals("无线蓝牙耳机", response.getProductName());

        // 📌 真落库
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_description WHERE product_id = ?",
                Integer.class, PRODUCT_ID);
        assertEquals(1, count, "前端契约保存后数据库里必须真的有一行");

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM product_description WHERE product_id = ?",
                String.class, PRODUCT_ID);
        assertTrue(content.contains("Hi-Fi 音质"), "正文必须落库");
        assertTrue(content.contains("- 主动降噪"), "核心卖点不能被悄悄丢掉");
    }

    @Test
    @DisplayName("前端契约保存：变体 SKU（t_product_sku.sku_code）也要能解析")
    void saveFrontendDescription_ResolvesVariantSku() {
        // 📌 前端「AI 商品描述」页展示的 SKU 是**变体 SKU**（带颜色/规格后缀），
        //    而商品列表页展示的是**商品级 SKU**（internal_sku）。
        //    两种都必须能解析 —— 只认一种 → 另一种「明明有这个商品却存不进去」。
        insertVariantSku("SKU-BLU-001-BLK", PRODUCT_ID);

        FrontendDescriptionSaveRequest request = new FrontendDescriptionSaveRequest();
        request.setSku("SKU-BLU-001-BLK");
        request.setBody("主动降噪，30 小时续航。");
        request.setLanguage("zh");
        request.setPlatform("amazon");

        FrontendDescriptionSaveResponse response =
                productDescriptionService.saveFrontendDescription(request);

        assertNotNull(response);
        assertTrue(response.getSaved());
        assertEquals(String.valueOf(PRODUCT_ID), response.getProductId());
        assertEquals("无线蓝牙耳机", response.getProductName());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_description WHERE product_id = ?",
                Integer.class, PRODUCT_ID);
        assertEquals(1, count, "变体 SKU 也必须真的落库");
    }

    @Test
    @DisplayName("前端契约保存：变体 SKU 查不到但商品级 SKU 能查到 → 也能解析")
    void saveFrontendDescription_FallsBackToProductSku() {
        FrontendDescriptionSaveRequest request = new FrontendDescriptionSaveRequest();
        request.setSku(SKU);   // 商品级 internal_sku
        request.setBody("正文");
        request.setLanguage("zh");
        request.setPlatform("amazon");

        FrontendDescriptionSaveResponse response =
                productDescriptionService.saveFrontendDescription(request);

        assertNotNull(response);
        assertEquals(String.valueOf(PRODUCT_ID), response.getProductId());
    }

    @Test
    @DisplayName("前端契约保存：SKU 查不到 → 抛 BizException，且一行都不许写")
    void saveFrontendDescription_UnknownSku_ThrowsAndWritesNothing() {
        FrontendDescriptionSaveRequest request = new FrontendDescriptionSaveRequest();
        request.setSku("SKU-NOT-EXIST");
        request.setBody("随便写点什么");

        BizException e = assertThrows(BizException.class,
                () -> productDescriptionService.saveFrontendDescription(request));
        assertTrue(e.getMessage().contains("SKU"), "错误提示要说清是 SKU 的问题：" + e.getMessage());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_description", Integer.class);
        assertEquals(0, count, "SKU 解析失败时不能写进任何一行（不能静默成功）");
    }

    @Test
    @DisplayName("前端契约保存：同一个 SKU 重复保存 → 一行，版本号递增")
    void saveFrontendDescription_Repeated_VersionIncreases() {
        FrontendDescriptionSaveRequest request = new FrontendDescriptionSaveRequest();
        request.setSku(SKU);
        request.setBody("第一版");
        request.setLanguage("zh");
        request.setPlatform("general");

        FrontendDescriptionSaveResponse first =
                productDescriptionService.saveFrontendDescription(request);
        assertEquals(1, first.getVersionNumber());

        request.setBody("第二版");
        FrontendDescriptionSaveResponse second =
                productDescriptionService.saveFrontendDescription(request);
        assertEquals(2, second.getVersionNumber());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_description WHERE product_id = ?",
                Integer.class, PRODUCT_ID);
        assertEquals(1, count, "重复保存不能多出第二行");
    }
}
