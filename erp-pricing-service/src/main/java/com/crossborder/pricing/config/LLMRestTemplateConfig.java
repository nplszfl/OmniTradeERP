package com.crossborder.pricing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * LLM 专用 RestTemplate（**带超时**）
 *
 * <p>📌 **为什么要单独一个 bean，而不是 {@code new RestTemplate()}**：
 * 无参构造的 connect/read timeout **默认无限** → LLM 服务端挂起时调用会**一直挂着**
 * → 定时任务被拖住 → 整批定价建议都生成不出来，而且日志里什么异常都没有（卡住 ≠ 报错）。
 * （erp-ai-assistant-service / erp-product-description-service 目前就是这个写法。）
 *
 * <p>📌 **做成 bean 的另一个好处**：单测可以用 {@code MockRestServiceServer} 接上它，
 * 不需要真的调 LLM 就能验证「请求构造对不对」和「失败时是不是如实降级」。
 */
@Configuration
public class LLMRestTemplateConfig {

    @Bean("llmRestTemplate")
    public RestTemplate llmRestTemplate(LLMConfig llmConfig) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = llmConfig != null && llmConfig.getTimeout() > 0 ? llmConfig.getTimeout() : 30000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }
}
