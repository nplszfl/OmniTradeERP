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

    /** API Base URL */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 超时时间（毫秒） */
    private int timeout = 30000;

    /** 最大Token数 */
    private int maxTokens = 2000;

    /** Temperature */
    private double temperature = 0.7;

    /** Provider: deepseek, openai */
    private String provider = "deepseek";

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }
}