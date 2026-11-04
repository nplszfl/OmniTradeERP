package com.crossborder.productdescription.config;

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
}