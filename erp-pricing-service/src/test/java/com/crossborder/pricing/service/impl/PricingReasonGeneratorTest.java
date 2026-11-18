package com.crossborder.pricing.service.impl;

import com.crossborder.pricing.config.LLMConfig;
import com.crossborder.pricing.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 定价建议的「AI 原因生成」单测
 *
 * <p>📌 <b>为什么不需要真的调 LLM 也能证明关键事实</b>：
 * <ul>
 *   <li><b>请求构造对不对</b>：MockRestServiceServer 断言 URL / 头 / 请求体 → 证明「Anthropic 协议接对了」；</li>
 *   <li><b>失败时是不是如实降级</b>：模拟 401 → 断言 {@code aiGenerated=false}、原因里写明「AI 调用失败」、{@code usage=null}；</li>
 *   <li><b>没配 key 时是不是如实降级</b>：断言原因里写明「AI 未启用」。</li>
 * </ul>
 * → 这三条是「**不撒谎**」的契约（规则 18 / 19），**比「真调一次」更适合放进 CI**（不依赖外部凭据）。
 */
class PricingReasonGeneratorTest {

    private LLMConfig llmConfig;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PricingReasonGenerator generator;

    @BeforeEach
    void setUp() {
        llmConfig = new LLMConfig();
        llmConfig.setEnabled(true);
        llmConfig.setApiKey("test-key");
        llmConfig.setProvider("anthropic");
        llmConfig.setBaseUrl("https://api.minimaxi.com/anthropic");
        llmConfig.setModel("MiniMax-M3");

        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        generator = new PricingReasonGenerator(llmConfig, restTemplate);
    }

    private PricingReasonGenerator.Reason generate() {
        return generator.generate(product(),
                new BigDecimal("142.47"), new BigDecimal("120.00"),
                new BigDecimal("125.00"), 3L, new BigDecimal("62.47"));
    }

    @Test
    @DisplayName("Anthropic 协议：路径 / 头 / 请求体都要对，且 usage 取 API 真值")
    void anthropicRequestShape() {
        server.expect(requestTo("https://api.minimaxi.com/anthropic/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"system\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MiniMax-M3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("竞品均价")))
                .andRespond(withSuccess(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"竞品均价 125 元高于建议价，故维持当前定价水平。\"}],"
                                + "\"usage\":{\"input_tokens\":120,\"output_tokens\":30}}",
                        MediaType.APPLICATION_JSON));

        PricingReasonGenerator.Reason reason = generate();

        server.verify();
        assertTrue(reason.aiGenerated(), "LLM 成功时 aiGenerated 必须为 true");
        assertEquals("竞品均价 125 元高于建议价，故维持当前定价水平。", reason.text());
        // 📌 **usage 必须是 API 真值**（120/30），不是本地估算
        assertNotNull(reason.usage());
        assertEquals(120, reason.usage().promptTokens());
        assertEquals(30, reason.usage().completionTokens());
    }

    @Test
    @DisplayName("401 → 如实降级：aiGenerated=false + 原因写明「AI 调用失败」+ usage=null")
    void unauthorizedDegradesHonestly() {
        server.expect(requestTo("https://api.minimaxi.com/anthropic/v1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        PricingReasonGenerator.Reason reason = generate();

        assertFalse(reason.aiGenerated(), "调用失败时 aiGenerated 必须为 false");
        assertTrue(reason.text().contains("AI 调用失败"), "原因里必须写明 AI 调用失败，实际：" + reason.text());
        assertTrue(reason.text().contains("规则定价"), "必须回落到规则模板，实际：" + reason.text());
        assertNull(reason.usage(), "拿不到用量就是 null，不估算");
        assertNotNull(reason.skipReason(), "必须给出跳过 AI 的原因");
    }

    @Test
    @DisplayName("llm.enabled=false → 如实降级：原因写明「AI 未启用」，且**不发任何请求**")
    void disabledDegradesHonestly() {
        llmConfig.setEnabled(false);
        server.expect(ExpectedCount.never(), requestTo("http://never-called.invalid"));

        PricingReasonGenerator.Reason reason = generate();

        assertFalse(reason.aiGenerated());
        assertTrue(reason.text().contains("AI 未启用"), "原因里必须写明 AI 未启用，实际：" + reason.text());
        assertNull(reason.usage());
    }

    @Test
    @DisplayName("OpenAI 兼容协议：走 Bearer 头 + choices 解析（别只认 Anthropic）")
    void openAiCompatiblePath() {
        llmConfig.setProvider("deepseek");
        llmConfig.setBaseUrl("https://api.deepseek.com");
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"成本 80 元是主要约束。\"}}],"
                                + "\"usage\":{\"prompt_tokens\":80,\"completion_tokens\":12,\"total_tokens\":92}}",
                        MediaType.APPLICATION_JSON));

        PricingReasonGenerator.Reason reason = generate();

        server.verify();
        assertTrue(reason.aiGenerated());
        assertEquals("成本 80 元是主要约束。", reason.text());
        assertNotNull(reason.usage());
        assertEquals(92, reason.usage().totalTokens());
    }

    private Product product() {
        Product p = new Product();
        p.setId(1L);
        p.setProductCode("PROD-001");
        p.setProductName("无线蓝牙耳机");
        p.setCostPrice(new BigDecimal("80.00"));
        p.setSellingPrice(new BigDecimal("120.00"));
        return p;
    }
}
