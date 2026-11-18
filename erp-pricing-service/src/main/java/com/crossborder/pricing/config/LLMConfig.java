package com.crossborder.pricing.config;

import com.crossborder.erp.llm.LLMEndpointResolver;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM API 配置（定价建议的「建议原因」生成）
 *
 * <p>📌 <b>本模块的 AI 边界（说清楚，避免对外谎报，规则 18 / 56）</b>：
 * <ul>
 *   <li><b>定价数字</b>（建议价 / 预期利润 / 置信度）= <b>确定性规则</b>算的
 *       （竞品 + 成本 + 需求加权，见 {@code DynamicPricingServiceImpl}）→ <b>不调用 LLM</b>，可复现；</li>
 *   <li><b>建议原因（人话解释）</b> = <b>LLM 生成</b> —— 这是 LLM 的天然落点：
 *       把规则算出的因子翻译成「为什么建议涨到 79.90 而不是 69.90」。</li>
 * </ul>
 * → 所以对外的 {@code strategy} 仍然如实写「多因素规则定价（…；非 LLM）」，
 * <b>指的是数字</b>；而 {@code reason} 是 AI 写的，调用方能看见（{@code factors.aiGenerated}）。
 *
 * <p>📌 <b>端点拼接的唯一事实来源 = {@code LLMEndpointResolver}</b>（不要在本模块自己拼路径）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {

    /** 是否启用真实 API 调用（默认 false → 建议原因用规则模板，并如实标注「AI 未启用」） */
    private boolean enabled = false;

    /** API Key */
    private String apiKey = "";

    /**
     * API 根地址（唯一事实来源 = {@code LLMEndpointResolver}，带不带 {@code /v1} 都行）
     *
     * <p>📌 与 application.yml 的默认值**保持一致**（不一致 = 其中一处是死代码）。
     */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 请求超时时间（毫秒） */
    private int timeout = 30000;

    /** 最大 Token 数（建议原因只要一两句话 → 单独用小值，避免浪费） */
    private int maxTokens = 2000;

    /** Temperature（建议原因是「解释」不是「创作」→ 低温更稳定） */
    private double temperature = 0.7;

    /** Provider 类型: deepseek / openai / azure / ollama / openai-compatible / anthropic */
    private String provider = "deepseek";

    /** Azure 的 deployment 名（仅 provider=azure 需要） */
    private String deployment = "";

    /** Azure 的 api-version（仅 provider=azure 需要） */
    private String apiVersion = "";

    /**
     * 是否已配置可用的 API Key
     */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 启动时校验 provider（<b>拼错就启动失败</b>，而不是静默降级）
     *
     * <p>📌 同 erp-ai-assistant-service 的做法：只在 {@code enabled=true} 时校验
     * —— 没启用真实调用时 provider 填什么都没影响，不该拦着启动。
     */
    @jakarta.annotation.PostConstruct
    public void validateProviderOnStartup() {
        if (!enabled) {
            return;
        }
        try {
            LLMEndpointResolver.validateProvider(provider);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "LLM 配置错误（provider=" + provider + "）：" + e.getMessage(), e);
        }
        if (LLMEndpointResolver.isAzure(provider)) {
            if (deployment == null || deployment.isBlank()) {
                throw new IllegalStateException(
                        "LLM 配置错误：provider=azure 时必须配置 llm.deployment（模型部署名）");
            }
            if (apiVersion == null || apiVersion.isBlank()) {
                throw new IllegalStateException(
                        "LLM 配置错误：provider=azure 时必须配置 llm.api-version（如 2024-02-01）");
            }
        }
    }
}
