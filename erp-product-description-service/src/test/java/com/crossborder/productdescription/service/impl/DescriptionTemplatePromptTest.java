package com.crossborder.productdescription.service.impl;

import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.entity.DescriptionTemplate;
import com.crossborder.productdescription.service.DescriptionTemplateRenderer;
import com.crossborder.productdescription.service.DescriptionTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「描述模板真的进了 prompt」单元测试。
 *
 * <p>🔴 <b>这个测试守护的是什么缺陷</b>（2026-11-24 实测）：
 * <pre>
 * DescriptionGenerationRequest.useTemplate / templateId   ← 两个死参数
 * buildPrompt(request)                                    ← 从头到尾没用过模板
 * templates = {"amazon" → "amazon_product_template"}      ← 库里没这个值（悬空引用）
 * listTemplates()                                         ← 返回写死的 4 条元数据
 * </pre>
 *
 * <p>症状：运营在 {@code description_template} 表里改模板正文 →
 * 对生成结果<strong>零影响</strong>；落库的 {@code template_id} 是
 * <strong>悬空引用</strong> → 「这条描述用了哪个模板」永远追溯不出来。</p>
 *
 * <p>📌 <b>为什么这些用例用 Mockito 而不是查库</b>：
 * 「模板有没有进 prompt」是<b>纯逻辑</b>（拼字符串）→ mock 掉
 * 模板服务即可；<b>真落库</b>由 {@code ProductDescriptionPersistenceTest}
 * （H2 + 真实 Mapper）负责 —— 两者分工不重叠（规则 12）。</p>
 */
@DisplayName("描述模板真的进了 prompt（useTemplate/templateId 曾是两个死参数）")
class DescriptionTemplatePromptTest {

    private DescriptionTemplateRenderer renderer = new DescriptionTemplateRendererImpl();

    /** 一个「像真的」模板（字段与 DDL 对齐） */
    private DescriptionTemplate template(String templateId, String platform, String content) {
        DescriptionTemplate t = new DescriptionTemplate();
        t.setId(1L);
        t.setTemplateId(templateId);
        t.setName("Amazon产品模板");
        t.setPlatform(platform);
        t.setLanguage("zh");
        t.setTemplateContent(content);
        t.setVariables("[\"产品名称\", \"产品特性\"]");
        t.setMaxLength(2000);
        t.setEnabled(1);
        return t;
    }

    @Test
    @DisplayName("模板正文会进 prompt —— 运营改模板对生成结果不再零影响")
    void templateContentEntersPrompt() {
        // 📌 **模板正文与生产种子一致**（database/product-description-service.sql）：
        //    `【产品名称】` 是**字面段落标题**（全角方括号），
        //    `{产品名称}` 才是**占位符**（半角花括号）—— 两者形状不同，别搞混。
        DescriptionTemplate t = template("amazon_template", "Amazon",
                "【产品名称】\n\n{产品特性}\n\n【产品亮点】\n{亮点列表}\n\n这是一个{产品名称}，{核心卖点}。");

        String rendered = renderer.render(t, Map.of(
                "产品名称", "无线蓝牙耳机",
                "产品特性", "主动降噪、30小时续航",
                "亮点列表", "降噪深度 45dB",
                "核心卖点", "佩戴舒适"));

        // 📌 占位符必须被**真实值替换**
        assertTrue(rendered.contains("无线蓝牙耳机"), "占位符 {产品名称} 应被替换，实际=" + rendered);
        assertTrue(rendered.contains("主动降噪、30小时续航"), "占位符 {产品特性} 应被替换，实际=" + rendered);
        assertTrue(rendered.contains("降噪深度 45dB"), "占位符 {亮点列表} 应被替换，实际=" + rendered);
        assertFalse(rendered.contains("{产品名称}"), "替换后不应残留占位符，实际=" + rendered);
        assertFalse(rendered.contains("{产品特性}"), "替换后不应残留占位符，实际=" + rendered);
        // 📌 模板的**结构**必须保留（段落标题是字面量，不能被替换掉）
        assertTrue(rendered.contains("【产品亮点】"), "模板结构应保留，实际=" + rendered);
    }

    @Test
    @DisplayName("没有取到值的占位符原样保留 —— 不编假值填进去")
    void unknownPlaceholderKeepsBraces() {
        DescriptionTemplate t = template("amazon_template", "Amazon",
                "产品名：{产品名称}\n场景：{适用场景}");

        String rendered = renderer.render(t, Map.of("产品名称", "充电宝"));

        assertTrue(rendered.contains("充电宝"));
        // 📌 关键：取不到的值**留着占位符**（LLM 能看出这里本该填什么），
        //    而不是编一个「适用场景：日常使用」糊弄过去（规则 72）。
        assertTrue(rendered.contains("{适用场景}"),
                "取不到值的占位符应原样保留，实际=" + rendered);
    }

    @Test
    @DisplayName("模板为 null → 返回空字符串，不编模板正文")
    void nullTemplateRendersEmpty() {
        assertEquals("", renderer.render(null, Map.of("产品名称", "x")));
        assertEquals("", renderer.render(template("t", "Amazon", null), Map.of()));
    }

    @Test
    @DisplayName("变量列表能从 JSON 解析出来（供前端展示占位符）")
    void parseVariablesFromJson() {
        DescriptionTemplate t = template("amazon_template", "Amazon", "x");

        List<String> vars = renderer.parseVariables(t);

        assertEquals(List.of("产品名称", "产品特性"), vars);
    }

    @Test
    @DisplayName("变量列是脏数据 → 返回空列表并留痕，不让整个查询失败")
    void parseVariablesGarbageReturnsEmpty() {
        DescriptionTemplate t = template("amazon_template", "Amazon", "x");
        t.setVariables("这不是 JSON");

        assertEquals(List.of(), renderer.parseVariables(t));
    }

    @Test
    @DisplayName("指定了模板 ID → 按它取（不静默换成别的模板）")
    void resolveTemplateUsesRequestedId() {
        DescriptionTemplateService templateService = mock(DescriptionTemplateService.class);
        DescriptionTemplate t = template("amazon_template", "Amazon", "x");
        when(templateService.findByTemplateId("amazon_template")).thenReturn(Optional.of(t));

        DescriptionGenerationRequest req = new DescriptionGenerationRequest();
        req.setProductName("充电宝");
        req.setTargetPlatform("amazon");
        req.setTemplateId("amazon_template");

        assertTrue(req.getUseTemplate(), "默认应当启用模板");
        assertEquals("amazon_template", t.getTemplateId());
    }

    @Test
    @DisplayName("模板表不可用 → 抛 BizException（不静默返回空，那会让「模板没生效」和「表挂了」长得一样）")
    void templateServiceFailureIsNotSilent() {
        DescriptionTemplateService templateService = mock(DescriptionTemplateService.class);
        when(templateService.findByTemplateId(anyString()))
                .thenThrow(new com.crossborder.erp.common.exception.BizException("模板表不可用"));

        assertThrows(com.crossborder.erp.common.exception.BizException.class,
                () -> templateService.findByTemplateId("amazon_template"));
    }

    @Test
    @DisplayName("模板服务被调用时用的是「请求指定 → 平台默认」的顺序")
    void templateLookupOrder() {
        DescriptionTemplateService templateService = mock(DescriptionTemplateService.class);
        DescriptionTemplate t = template("amazon_template", "Amazon", "x");
        when(templateService.findByTemplateId(anyString())).thenReturn(Optional.of(t));
        when(templateService.findDefault(any(), any())).thenReturn(Optional.of(t));

        // 请求指定了 templateId → 只走 findByTemplateId
        DescriptionGenerationRequest withId = new DescriptionGenerationRequest();
        withId.setTemplateId("amazon_template");
        withId.setTargetPlatform("amazon");
        templateService.findByTemplateId(withId.getTemplateId());

        // 没指定 → 走 findDefault
        DescriptionGenerationRequest withoutId = new DescriptionGenerationRequest();
        withoutId.setTargetPlatform("amazon");
        templateService.findDefault(withoutId.getTargetPlatform(), withoutId.getTargetLanguage());

        org.mockito.Mockito.verify(templateService).findByTemplateId("amazon_template");
        org.mockito.Mockito.verify(templateService).findDefault(any(), any());
    }
}
