package com.crossborder.productdescription.config;

import com.crossborder.erp.llm.LLMEndpointResolver;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 产品描述生成LLM配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {

    /** 是否启用真实API */
    private boolean enabled = false;

    /** API Key */
    private String apiKey = "";

    /**
     * API Base URL（<b>API 根地址</b>）
     *
     * <p>📌 <b>约定</b>（唯一事实来源 = {@code LLMEndpointResolver}）：
     * <b>带不带 {@code /v1} 都行</b>，解析器会归一化。
     *
     * <p>📌 ⚠️ 两处默认值必须一致 —— 不一致 = 其中一处是死代码。
     */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 超时时间（毫秒） */
    private int timeout = 30000;

    /** 最大Token数 */
    private int maxTokens = 2000;

    /** Temperature */
    private double temperature = 0.7;

    /** Provider: deepseek, openai, azure */
    private String provider = "deepseek";

    /**
     * Azure 的 deployment 名（模型部署名）
     *
     * <p>📌 只有 provider=azure 时才需要。
     */
    private String deployment = "";

    /**
     * Azure 的 api-version（如 {@code 2024-02-01}）
     *
     * <p>📌 只有 provider=azure 时才需要。
     */
    private String apiVersion = "";

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 启动时校验 provider（<b>拼错就启动失败</b>，而不是静默降级）
     *
     * <p>🔴 <b>为什么必须在启动时校验</b>（2026-11-04 实测）：
     * {@code LLMEndpointResolver.isSupportedProvider()} 之前<b>只被单测调用过，
     * 运行期没人调</b> → provider 拼错（比如 {@code deepseel}）一路畅通，
     * 拼出一个必然 404 的 URL → catch 吞掉 → <b>静默降级成模拟回复</b>。
     * <p>→ 界面上 AI 生成一切正常，但 <b>AI 从来没真的工作过</b>。
     * <p>→ 现在 {@code @PostConstruct} 在启动时校验 → <b>宁可起不来，也不要假装能工作</b>。
     *
     * <p>📌 只在 {@code enabled=true}（真的要调真实 API）时才校验：
     * 没启用真实调用时 provider 填什么都没影响，不该拦着启动。
     *
     * @throws IllegalStateException provider 不受支持时
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
        // 📌 Azure 的 URL 形状和别家完全不同，缺 deployment / api-version 必然 404
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