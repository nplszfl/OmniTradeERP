package com.crossborder.aiassistant.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLMConfig 的 provider 启动校验测试
 *
 * <p>🔴 <b>这个测试守护的是什么缺陷</b>（2026-11-04 实测）：
 * <pre>
 * LLMEndpointResolver.isSupportedProvider() 写了，但<b>只被单测调用过，
 * 运行期没有任何地方调它</b>。
 * → provider 拼错（比如 deepseel）一路畅通 → 拼出一个必然 404 的 URL
 * → HTTP 客户端抛异常 → catch 吞掉 → <b>静默降级成模拟回复</b>
 * → 界面上 AI 聊天一切正常，<b>但 AI 从来没真的工作过</b>
 * </pre>
 *
 * <p>→ 现在 {@code @PostConstruct} 在启动时校验 → <b>宁可起不来，也不要假装能工作</b>。
 *
 * <p>📌 <b>不要写「假测试」</b>（知识库规则 48）：
 * 这里测的是<b>真实的配置类</b>，不是自己写的本地方法。
 */
class LLMConfigProviderValidationTest {

    private LLMConfig config(String provider, boolean enabled) {
        LLMConfig c = new LLMConfig();
        c.setProvider(provider);
        c.setEnabled(enabled);
        c.setApiKey("sk-test-key");
        return c;
    }

    @Test
    @DisplayName("provider 拼错时启动必须失败 —— 不能让它走到 404 再静默降级")
    void typoProviderFailsFast() {
        LLMConfig c = config("deepseel", true);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> c.validateProviderOnStartup());
        assertTrue(e.getMessage().contains("deepseel"),
                "错误信息要带上错的值：" + e.getMessage());
        assertTrue(e.getMessage().contains("openai-compatible"),
                "错误信息要告诉用户该怎么改：" + e.getMessage());
    }

    @Test
    @DisplayName("provider 为空也要失败 —— 不能默默按默认处理")
    void blankProviderFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> config(null, true).validateProviderOnStartup());
        assertThrows(IllegalStateException.class,
                () -> config("  ", true).validateProviderOnStartup());
    }

    @Test
    @DisplayName("受支持的 provider 启动校验要通过")
    void supportedProviderPasses() {
        assertDoesNotThrow(() -> config("deepseek", true).validateProviderOnStartup());
        assertDoesNotThrow(() -> config("openai", true).validateProviderOnStartup());
        assertDoesNotThrow(() -> config("DEEPSEEK", true).validateProviderOnStartup());
    }

    @Test
    @DisplayName("provider=azure 缺 deployment / api-version 必须失败 —— 缺一个必然 404")
    void azureRequiresDeploymentAndApiVersion() {
        LLMConfig noDeployment = config("azure", true);
        IllegalStateException e1 = assertThrows(IllegalStateException.class,
                () -> noDeployment.validateProviderOnStartup());
        assertTrue(e1.getMessage().contains("deployment"), e1.getMessage());

        LLMConfig noApiVersion = config("azure", true);
        noApiVersion.setDeployment("gpt-4o");
        IllegalStateException e2 = assertThrows(IllegalStateException.class,
                () -> noApiVersion.validateProviderOnStartup());
        assertTrue(e2.getMessage().contains("api-version"), e2.getMessage());

        // 配齐了才通过
        LLMConfig ok = config("azure", true);
        ok.setDeployment("gpt-4o");
        ok.setApiVersion("2024-02-01");
        assertDoesNotThrow(() -> ok.validateProviderOnStartup());
    }

    @Test
    @DisplayName("没启用真实调用时不拦启动 —— provider 填什么都不影响")
    void disabledDoesNotBlockStartup() {
        // enabled=false 时本来就走模拟回复，不该因为 provider 拼错就起不来
        assertDoesNotThrow(() -> config("deepseel", false).validateProviderOnStartup());
        assertDoesNotThrow(() -> config("", false).validateProviderOnStartup());
    }
}
