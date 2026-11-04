package com.crossborder.erp.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LLMEndpointResolver} 契约测试。
 *
 * <p>📌 **这个测试是为了钉死一个真实存在的缺陷**（2026-11-04 实测）：
 * <pre>
 * application.yml:  base-url: ${LLM_BASE_URL:https://api.deepseek.com/v1}
 * 代码:             apiUrl = baseUrl + "/v1/chat/completions"
 * 结果:             https://api.deepseek.com/v1/v1/chat/completions   ← 双 /v1，必然 404
 * </pre>
 * → 真实 LLM 调用 404 → catch → 静默降级成模拟回复
 * → **AI 功能从来没真的调通过，但一切都是绿的**。
 *
 * <p>📌 改之前这个模块**一个 URL 断言都没有**（22 道闸门也全过）——
 * 这就是「调用第三方库的公开方法最容易缺测试」的另一个例子。
 */
class LLMEndpointResolverTest {

    // ---------------------------------------------------------------- 核心：双 /v1

    @Test
    @DisplayName("base-url 带 /v1 时不能拼出双 /v1 —— 这就是 2026-11-04 的真实缺陷")
    void baseUrlWithV1MustNotProduceDoubleV1() {
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("https://api.deepseek.com/v1"));
    }

    @Test
    @DisplayName("base-url 不带 /v1 时结果一致 —— 两种写法表达同一个意思")
    void baseUrlWithoutV1GivesSameResult() {
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("https://api.deepseek.com"));
    }

    @Test
    @DisplayName("已经被手工拼成 /v1/v1 的配置要能自愈")
    void alreadyDoubledV1SelfHeals() {
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("https://api.deepseek.com/v1/v1"));
    }

    @Test
    @DisplayName("带尾部斜杠 / 多余斜杠都要归一化")
    void trailingSlashesAreNormalized() {
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("https://api.deepseek.com/"));
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("https://api.deepseek.com/v1/"));
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("  https://api.deepseek.com/v1  "));
    }

    @Test
    @DisplayName("自定义前缀网关要保留前缀，只剥版本段")
    void customGatewayPrefixIsPreserved() {
        assertEquals("https://gw.example.com/openai/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("https://gw.example.com/openai/v1"));
        assertEquals("https://gw.example.com/openai/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("https://gw.example.com/openai"));
    }

    @Test
    @DisplayName("OpenAI 官方域名同样成立")
    void openAiOfficialHost() {
        assertEquals("https://api.openai.com/v1/chat/completions",
                LLMEndpointResolver.openAiCompatibleUrl("https://api.openai.com/v1"));
    }

    // ---------------------------------------------------------------- Azure 分支

    @Test
    @DisplayName("Azure 走自己的路径形状 —— 不能用 OpenAI 兼容的拼法")
    void azureUsesItsOwnPathShape() {
        String url = LLMEndpointResolver.chatCompletionsUrl(
                "https://myco.openai.azure.com", "azure", "gpt-4o", "2024-02-01");
        assertEquals("https://myco.openai.azure.com/openai/deployments/gpt-4o"
                + "/chat/completions?api-version=2024-02-01", url);
    }

    @Test
    @DisplayName("Azure 缺 deployment / api-version 要立刻报错，不能拼出一个 404 URL")
    void azureRequiresDeploymentAndApiVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                LLMEndpointResolver.chatCompletionsUrl(
                        "https://myco.openai.azure.com", "azure", null, "2024-02-01"));
        assertThrows(IllegalArgumentException.class, () ->
                LLMEndpointResolver.chatCompletionsUrl(
                        "https://myco.openai.azure.com", "azure", "gpt-4o", null));
    }

    @Test
    @DisplayName("provider 大小写不敏感")
    void azureDetectionIsCaseInsensitive() {
        assertTrue(LLMEndpointResolver.isAzure("Azure"));
        assertTrue(LLMEndpointResolver.isAzure("AZURE"));
        assertTrue(LLMEndpointResolver.isAzure(" azure "));
    }

    // ---------------------------------------------------------------- provider 支持性

    @Test
    @DisplayName("已知的 OpenAI 兼容 provider 都要认")
    void knownProvidersAreSupported() {
        for (String p : new String[]{"deepseek", "openai", "azure", "ollama",
                "qwen", "moonshot", "zhipu", "openai-compatible"}) {
            assertTrue(LLMEndpointResolver.isSupportedProvider(p), p + " 应当受支持");
        }
    }

    @Test
    @DisplayName("空 provider 不受支持 —— 不能默默按默认处理")
    void blankProviderIsNotSupported() {
        assertTrue(!LLMEndpointResolver.isSupportedProvider(""));
        assertTrue(!LLMEndpointResolver.isSupportedProvider(null));
    }

    // ---------------------------------------------------------------- validateProvider

    @Test
    @DisplayName("validateProvider 对受支持的 provider 不报错")
    void validateProviderAcceptsSupportedOnes() {
        for (String p : LLMEndpointResolver.SUPPORTED_PROVIDERS) {
            assertDoesNotThrow(() -> LLMEndpointResolver.validateProvider(p), p + " 应当通过校验");
        }
        // 大小写不敏感
        assertDoesNotThrow(() -> LLMEndpointResolver.validateProvider("DEEPSEEK"));
        assertDoesNotThrow(() -> LLMEndpointResolver.validateProvider("  Azure  "));
    }

    @Test
    @DisplayName("provider 拼错要立刻报错，且错误信息要说清怎么改")
    void validateProviderRejectsTypoWithActionableMessage() {
        // 🔴 这正是 2026-11-04 的真实缺陷：isSupportedProvider 只被单测调过，
        //    运行期没人调 → provider 拼错 → 拼出必然 404 的 URL → 静默降级成 mock
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LLMEndpointResolver.validateProvider("deepseel"));
        String msg = e.getMessage();
        assertTrue(msg.contains("deepseel"), "错误信息要带上错的值：" + msg);
        assertTrue(msg.contains("openai-compatible"), "错误信息要告诉用户该怎么改：" + msg);
    }

    @Test
    @DisplayName("空 provider 也要报错 —— 不能默默按默认处理")
    void validateProviderRejectsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> LLMEndpointResolver.validateProvider(""));
        assertThrows(IllegalArgumentException.class,
                () -> LLMEndpointResolver.validateProvider(null));
    }

    @Test
    @DisplayName("SUPPORTED_PROVIDERS 与 isSupportedProvider 必须一致 —— 单一事实来源")
    void supportedProvidersListMatchesPredicate() {
        for (String p : LLMEndpointResolver.SUPPORTED_PROVIDERS) {
            assertTrue(LLMEndpointResolver.isSupportedProvider(p),
                    p + " 在列表里就该被 isSupportedProvider 认可");
        }
        // 反向：常见拼法不能漏
        for (String p : new String[]{"deepseek", "openai", "azure", "openai-compatible"}) {
            assertTrue(LLMEndpointResolver.SUPPORTED_PROVIDERS.contains(p),
                    p + " 应当出现在 SUPPORTED_PROVIDERS 里");
        }
    }

    // ---------------------------------------------------------------- 参数校验

    @Test
    @DisplayName("base-url 为空要立刻报错，而不是拼出相对路径")
    void blankBaseUrlThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> LLMEndpointResolver.openAiCompatibleUrl(""));
        assertThrows(IllegalArgumentException.class,
                () -> LLMEndpointResolver.openAiCompatibleUrl(null));
        assertThrows(IllegalArgumentException.class,
                () -> LLMEndpointResolver.openAiCompatibleUrl("   "));
    }

    // ---------------------------------------------------------------- 归一化

    @Test
    @DisplayName("normalizeRoot 剥掉版本段但保留前缀")
    void normalizeRootKeepsPrefix() {
        assertEquals("https://api.deepseek.com",
                LLMEndpointResolver.normalizeRoot("https://api.deepseek.com/v1"));
        assertEquals("https://gw.example.com/openai",
                LLMEndpointResolver.normalizeRoot("https://gw.example.com/openai/v1"));
        assertEquals("https://api.deepseek.com",
                LLMEndpointResolver.normalizeRoot("https://api.deepseek.com/v1/v1/"));
    }
}
