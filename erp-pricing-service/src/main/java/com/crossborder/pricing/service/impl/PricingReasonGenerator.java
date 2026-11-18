package com.crossborder.pricing.service.impl;

import com.crossborder.erp.llm.LLMEndpointResolver;
import com.crossborder.erp.llm.LLMTokenUsage;
import com.crossborder.pricing.config.LLMConfig;
import com.crossborder.pricing.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定价建议的「建议原因」生成（<b>只生成文字，不碰数据库</b>）
 *
 * <p>📌 <b>边界说清楚（规则 18 / 56）</b>：
 * <ul>
 *   <li>这里的 <b>LLM 只负责写一句话的解释</b>；<b>数字（建议价/利润/置信度）全部是规则算的</b> → 可复现；</li>
 *   <li>LLM 不可用（没启用 / 没 key / 调用失败）→ <b>回落到规则模板</b>，
 *       并在原因里<b>如实写明</b>「AI 未启用」或「AI 调用失败：…」→ 绝不假装是模型写的；</li>
 *   <li>token 用量取 <b>API 返回的真值</b>，拿不到就是 {@code null}（不估算，规则 391）。</li>
 * </ul>
 *
 * <p>📌 抽成独立组件的原因：这一段逻辑（协议接线 + 降级诚实）**必须能被单测直接验证**，
 * 而它既不落库也不依赖 mapper → 不该被埋在生成器的实现里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PricingReasonGenerator {

    private final LLMConfig llmConfig;
    private final RestTemplate restTemplate;

    /**
     * 生成结果
     *
     * @param text       最终写进建议里的原因文本
     * @param aiGenerated 是否由 LLM 生成（false = 规则模板）
     * @param skipReason 未用 AI 的原因（aiGenerated=false 时有值）
     * @param usage      真实 token 用量（拿不到 = null）
     */
    public record Reason(String text, boolean aiGenerated, String skipReason, LLMTokenUsage usage) {
    }

    /**
     * 生成建议原因
     */
    public Reason generate(Product product, BigDecimal suggestedPrice, BigDecimal currentPrice,
                            BigDecimal competitorAvg, long competitorCount,
                            BigDecimal expectedProfit) {
        // ── 未配置 → 规则模板 + 如实标注 ──
        if (!llmConfig.isConfigured()) {
            return new Reason(
                    ruleReason(product, suggestedPrice, currentPrice, competitorAvg, competitorCount)
                            + "（AI 未启用：llm.enabled=false 或未配置 API Key）",
                    false, "llm.enabled=false 或未配置 API Key", null);
        }

        try {
            String apiUrl = LLMEndpointResolver.resolveApiUrl(
                    llmConfig.getBaseUrl(), llmConfig.getProvider(),
                    llmConfig.getDeployment(), llmConfig.getApiVersion());
            boolean anthropic = LLMEndpointResolver.isAnthropic(llmConfig.getProvider());

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", llmConfig.getModel());
            String system = "你是跨境电商定价分析师。用**一句话中文**解释这个定价建议的依据，"
                    + "要点出竞品、成本、利润三者中起决定作用的因素。不要编造数据，不要重复数字，"
                    + "不要输出任何解释性前后缀，直接给出一句话。";
            if (anthropic) {
                // 📌 Anthropic：`system` 是**顶层字段**，messages 里只有 user
                requestBody.put("system", system);
                requestBody.put("messages", List.of(Map.of("role", "user", "content", buildPrompt(
                        product, suggestedPrice, currentPrice, competitorAvg, competitorCount, expectedProfit))));
            } else {
                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", system));
                messages.add(Map.of("role", "user", "content", buildPrompt(
                        product, suggestedPrice, currentPrice, competitorAvg, competitorCount, expectedProfit)));
                requestBody.put("messages", messages);
            }
            requestBody.put("temperature", llmConfig.getTemperature());
            requestBody.put("max_tokens", llmConfig.getMaxTokens());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (anthropic) {
                headers.set(LLMEndpointResolver.ANTHROPIC_API_KEY_HEADER, llmConfig.getApiKey());
                headers.set("anthropic-version", LLMEndpointResolver.ANTHROPIC_VERSION);
            } else if (LLMEndpointResolver.isAzure(llmConfig.getProvider())) {
                headers.set("api-key", llmConfig.getApiKey());
            } else {
                headers.setBearerAuth(llmConfig.getApiKey());
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, new HttpEntity<>(requestBody, headers), Map.class);

            if (response.getBody() == null) {
                throw new IllegalStateException("LLM 响应体为空");
            }
            // 📌 **真实 token 用量**（拿不到 = null，不估算；规则 391）
            LLMTokenUsage usage = LLMEndpointResolver.extractTokenUsage(response.getBody(), anthropic);

            String text;
            if (anthropic) {
                text = LLMEndpointResolver.extractAnthropicText(response.getBody());
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices =
                        (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new IllegalStateException("LLM 响应缺少 choices");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                text = (String) message.get("content");
            }
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("LLM 返回内容为空");
            }
            return new Reason(text.trim(), true, null, usage);

        } catch (Exception e) {
            // 📌 **降级必须带原因，且必须能被调用方看见**（规则 13 / 19）
            log.error("定价建议原因生成失败，回落到规则模板: {}", e.getMessage());
            return new Reason(
                    ruleReason(product, suggestedPrice, currentPrice, competitorAvg, competitorCount)
                            + "（AI 调用失败：" + e.getMessage() + "）",
                    false, "LLM 调用失败：" + e.getMessage(), null);
        }
    }

    private String buildPrompt(Product product, BigDecimal suggestedPrice, BigDecimal currentPrice,
                                BigDecimal competitorAvg, long competitorCount,
                                BigDecimal expectedProfit) {
        StringBuilder sb = new StringBuilder();
        sb.append("商品：").append(product.getProductName())
                .append("（编码 ").append(product.getProductCode()).append("）\n");
        sb.append("当前售价：").append(currentPrice).append("\n");
        sb.append("建议售价：").append(suggestedPrice).append("\n");
        sb.append("成本价：").append(product.getCostPrice()).append("\n");
        sb.append("预期利润：").append(expectedProfit).append("\n");
        sb.append("竞品均价：").append(competitorAvg == null ? "无竞品数据" : competitorAvg)
                .append("（竞品数 ").append(competitorCount).append("）\n");
        sb.append("请用一句话中文解释这个建议价为什么合理。");
        return sb.toString();
    }

    /** 规则模板版原因（LLM 不可用时的回落）—— 数字全部来自真实数据 */
    private String ruleReason(Product product, BigDecimal suggestedPrice, BigDecimal currentPrice,
                               BigDecimal competitorAvg, long competitorCount) {
        StringBuilder sb = new StringBuilder("规则定价：");
        sb.append("当前 ").append(currentPrice).append(" → 建议 ").append(suggestedPrice).append("；");
        if (competitorAvg != null && competitorCount > 0) {
            sb.append("竞品均价 ").append(competitorAvg).append("（").append(competitorCount).append(" 个）；");
        } else {
            sb.append("无竞品数据；");
        }
        sb.append("成本 ").append(product.getCostPrice()).append("。");
        return sb.toString();
    }
}
