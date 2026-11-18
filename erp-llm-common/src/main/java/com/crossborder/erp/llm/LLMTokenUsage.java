package com.crossborder.erp.llm;

/**
 * LLM 调用的**真实** token 用量。
 *
 * <p>📌 **为什么要单独一个类型**（2026-11-18）：
 * 之前两个服务都用 {@code prompt.length()/4} **本地估算**，然后当成「真实用量」写进 API 响应
 * → **假事实**（规则 18/19：模拟回复编造 token 用量 = 假事实）。
 * → 现在改成：从 API 响应体的 {@code usage} 字段**原样透传**；
 * 响应里没有 usage 时 → {@code null}（**不知道**，而不是编一个看起来很像真的数字）。
 *
 * @param promptTokens     输入 token（Anthropic 叫 input_tokens，OpenAI 叫 prompt_tokens）
 * @param completionTokens 输出 token（Anthropic 叫 output_tokens，OpenAI 叫 completion_tokens）
 * @param totalTokens      总 token
 */
public record LLMTokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
