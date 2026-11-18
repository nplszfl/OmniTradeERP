package com.crossborder.erp.llm;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LLM 端点解析器 —— **LLM 端点 URL 的唯一事实来源**。
 *
 * <p>📌 **为什么要有这个类**（2026-11-04 实测）：
 * <pre>
 * 之前 erp-ai-assistant / erp-product-description 两个服务各自写死：
 *     String apiUrl = llmConfig.getBaseUrl() + "/v1/chat/completions";
 * 而 application.yml 里的默认值是：
 *     base-url: ${LLM_BASE_URL:https://api.deepseek.com/v1}      ← 带 /v1
 * → 拼出来：https://api.deepseek.com/v1/v1/chat/completions     ← 双 /v1
 * </pre>
 *
 * <p>🔴 **后果链**（这就是「看起来在工作、实际不对」）：
 * <ol>
 *   <li>真实 LLM 调用必然 404（HTTP 客户端把 404 也算异常 → 抛 RuntimeException）</li>
 *   <li>catch 之后<b>静默降级</b>成模拟回复 → 用户收到一段 canned 文本</li>
 *   <li>响应里 <code>model</code> 仍然写着 <code>deepseek-chat</code>，
 *       前端<b>无法区分</b>这是真 AI 回答还是假回答</li>
 *   <li>→ <b>AI 功能上线后从来没真的调通过一次</b>，但所有监控/单测/闸门都是绿的</li>
 * </ol>
 *
 * <p>📌 **同类根因**：<code>LLMConfig</code> 里的<b>类默认值</b>是
 * <code>https://api.deepseek.com</code>（不带 /v1，<b>和拼接是匹配的</b>），
 * 但 application.yml 的默认值<b>带 /v1</b>。
 * 因为 <code>@ConfigurationProperties</code> 里 <code>llm.base-url</code> <b>永远存在</b>，
 * 类默认值<b>永远不生效</b> → 真正生效的是那个和代码冲突的那个。
 * <p>→ <b>两处默认值不一致 = 一处是死代码</b>。这类缺陷单测和巡检都很难发现，
 * 因为「读配置」和「用配置」分处两个文件。
 *
 * <p>📌 <b>本类的约定</b>（唯一事实来源）：
 * <ul>
 *   <li><code>baseUrl</code> = API <b>根地址</b>，<b>带不带 {@code /v1} 都行</b>（本类会归一化）</li>
 *   <li>OpenAI 兼容协议（deepseek / openai / 任何 OpenAI 兼容网关）
 *       → 归一化后统一拼 {@code /v1/chat/completions}</li>
 *   <li>Azure OpenAI 的路径形状<b>完全不同</b>
 *       （{@code /openai/deployments/{deployment}/chat/completions?api-version=}）
 *       → <b>单独一条分支</b>，不共用 OpenAI 兼容的拼法</li>
 * </ul>
 *
 * <p>📌 <b>为什么放在 erp-common</b>：
 * 两个 AI 服务都要用，而各服务的 {@code @SpringBootApplication} 只扫自己的子包
 * （不含 {@code com.crossborder.erp.common}）→ <b>这里必须是纯静态工具类，
 * 不能是 Spring bean</b>（bean 不会被扫到 = 死代码）。静态方法不依赖组件扫描。
 *
 * <p>📌 <b>不要改成 Spring bean</b> —— 那会踩「组件扫描不到」这个本项目最大的隐性坑。
 */
public final class LLMEndpointResolver {

    /** OpenAI 兼容协议的版本路径 */
    private static final String OPENAI_V1 = "/v1";

    /** OpenAI 兼容协议的对话补全路径 */
    private static final String CHAT_COMPLETIONS = "/chat/completions";

    /** 📌 Anthropic 协议的对话路径（不是 {@code /chat/completions}） */
    private static final String ANTHROPIC_MESSAGES = "/messages";

    /**
     * 📌 Anthropic 协议的 api-version 头值。
     *
     * <p>📌 <b>为什么需要 Anthropic 分支</b>（2026-11-18 实测）：
     * Anthropic 协议的路径形状、请求头、请求体、响应体**全都和 OpenAI 兼容协议不一样**：
     * <ul>
     *   <li>路径：{@code POST {root}/v1/messages}（<b>不是</b> {@code /v1/chat/completions}）</li>
     *   <li>鉴权头：{@code x-api-key: <key>}（<b>不是</b> {@code Authorization: Bearer}）
     *       外加必填 {@code anthropic-version: 2023-06-01}</li>
     *   <li>请求体：{@code system} 是<b>顶层字段</b>（不是 messages 数组里的一条）</li>
     *   <li>响应体：{@code content[]} 是<b>内容块数组</b>（不是 {@code choices[0].message.content}）</li>
     * </ul>
     * → 拿 OpenAI 的拼法去调 Anthropic 端点 = <b>必然 401/404</b> → 静默降级成 mock → **AI 从来没真调过**。
     */
    public static final String ANTHROPIC_VERSION = "2023-06-01";

    /** 📌 Anthropic 协议的鉴权头名（不是 {@code Authorization}） */
    public static final String ANTHROPIC_API_KEY_HEADER = "x-api-key";

    private LLMEndpointResolver() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 解析对话补全的完整 URL。
     *
     * @param baseUrl    API 根地址（带不带 /v1 都行）
     * @param provider   provider 类型：{@code azure} 走 Azure 分支，其余走 OpenAI 兼容分支
     * @param deployment Azure 的 deployment 名（非 Azure 传 null 即可）
     * @param apiVersion Azure 的 api-version（非 Azure 传 null 即可）
     * @return 完整 URL
     */
    public static String chatCompletionsUrl(String baseUrl, String provider,
                                            String deployment, String apiVersion) {
        return resolveApiUrl(baseUrl, provider, deployment, apiVersion);
    }

    /**
     * 解析「要调的那个 URL」——<b>按 provider 分派</b>（端点拼接的唯一事实来源）。
     *
     * <p>📌 三个分支的路径形状<b>完全不同</b>，不能共用一个拼法：
     * <ul>
     *   <li>{@code azure} → {@code /openai/deployments/{d}/chat/completions?api-version=}（要 deployment + api-version）</li>
     *   <li>{@code anthropic} → {@code /v1/messages}（<b>鉴权头也不是 Bearer</b>）</li>
     *   <li>其余（deepseek/openai/ollama/自建兼容网关）→ {@code /v1/chat/completions}</li>
     * </ul>
     *
     * @param baseUrl    API 根地址（带不带 /v1 都行）
     * @param provider   provider 类型
     * @param deployment Azure 的 deployment 名（非 Azure 传 null 即可）
     * @param apiVersion Azure 的 api-version（非 Azure 传 null 即可）
     * @return 完整 URL
     */
    public static String resolveApiUrl(String baseUrl, String provider,
                                        String deployment, String apiVersion) {
        if (isAzure(provider)) {
            return azureChatCompletionsUrl(baseUrl, deployment, apiVersion);
        }
        if (isAnthropic(provider)) {
            return anthropicMessagesUrl(baseUrl);
        }
        return openAiCompatibleUrl(baseUrl);
    }

    /**
     * Anthropic 协议的对话 URL。
     *
     * <p>📌 形如 {@code https://api.minimaxi.com/anthropic/v1/messages}
     * （base-url 写 {@code .../anthropic} 或 {@code .../anthropic/v1} 都对 → 归一化后统一拼 {@code /v1/messages}）。
     *
     * @param baseUrl API 根地址
     * @return 完整 URL
     */
    public static String anthropicMessagesUrl(String baseUrl) {
        return normalizeRoot(baseUrl) + OPENAI_V1 + ANTHROPIC_MESSAGES;
    }

    /**
     * 从响应体里提取**真实的** token 用量。
     *
     * <p>📌 **为什么必须透传（2026-11-18 实测）**：
     * 之前两个服务都用 {@code prompt.length()/4} **本地估算** token 数，
     * 然后当成「真实用量」写进 API 响应 → **假事实**（规则 18/19：
     * 「模拟回复编造 token 用量 = 假事实」）。
     * → 调用方做成本核算/限额时，数字对不上却**没人发现**。
     *
     * <p>📌 两家的字段名不一样：
     * <ul>
     *   <li>Anthropic：{@code usage.input_tokens} / {@code usage.output_tokens}</li>
     *   <li>OpenAI 兼容：{@code usage.prompt_tokens} / {@code usage.completion_tokens} / {@code usage.total_tokens}</li>
     * </ul>
     *
     * @param body      响应体（可为 null）
     * @param anthropic 是否 Anthropic 协议
     * @return 真实用量；**响应里没有 usage 时返回 null**（宁可 null = 不知道，也不编）
     */
    @SuppressWarnings("unchecked")
    public static LLMTokenUsage extractTokenUsage(Map<String, Object> body, boolean anthropic) {
        if (body == null) {
            return null;
        }
        Object usageObj = body.get("usage");
        if (!(usageObj instanceof Map)) {
            return null;
        }
        Map<String, Object> usage = (Map<String, Object>) usageObj;
        Integer prompt = asInt(anthropic ? usage.get("input_tokens") : usage.get("prompt_tokens"));
        Integer completion = asInt(anthropic ? usage.get("output_tokens") : usage.get("completion_tokens"));
        if (prompt == null && completion == null) {
            return null;
        }
        Integer total = anthropic ? null : asInt(usage.get("total_tokens"));
        if (total == null) {
            total = (prompt == null ? 0 : prompt) + (completion == null ? 0 : completion);
        }
        return new LLMTokenUsage(prompt, completion, total);
    }

    /** 把响应里的数字安全地转成 Integer（JSON 里可能是 Long / Double / String）。 */
    private static Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 Anthropic 协议的响应体里提取文本。
     *
     * <p>📌 形状：{@code {"content":[{"type":"text","text":"..."}]}}
     * → 和 OpenAI 的 {@code choices[0].message.content} <b>完全不同</b>。
     * 📌 放在这里 = 协议形状的**唯一事实来源**（三个 AI 调用点共用，别各写一份）。
     *
     * @param body 响应体（可为 null）
     * @return 拼接后的文本；没有可用文本时返回 null
     */
    @SuppressWarnings("unchecked")
    public static String extractAnthropicText(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object contentObj = body.get("content");
        if (!(contentObj instanceof List)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object blockObj : (List<Object>) contentObj) {
            if (!(blockObj instanceof Map)) {
                continue;
            }
            Map<String, Object> block = (Map<String, Object>) blockObj;
            if ("text".equals(block.get("type"))) {
                Object text = block.get("text");
                if (text != null) {
                    sb.append(text);
                }
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * OpenAI 兼容协议（deepseek / openai / 各类兼容网关）的对话补全 URL。
     *
     * @param baseUrl API 根地址
     * @return 形如 {@code https://api.deepseek.com/v1/chat/completions}
     */
    public static String openAiCompatibleUrl(String baseUrl) {
        return normalizeRoot(baseUrl) + OPENAI_V1 + CHAT_COMPLETIONS;
    }

    /**
     * Azure OpenAI 的对话补全 URL。
     *
     * <p>📌 Azure 的路径形状和 OpenAI 兼容协议<b>完全不同</b>，
     * 而且需要 deployment 名和 api-version —— 缺一个就调不通。
     *
     * @param endpoint   Azure endpoint（形如 {@code https://xxx.openai.azure.com}）
     * @param deployment deployment 名
     * @param apiVersion api-version（如 {@code 2024-02-01}）
     * @return 形如 {@code https://xxx.openai.azure.com/openai/deployments/gpt-4o/chat/completions?api-version=2024-02-01}
     */
    public static String azureChatCompletionsUrl(String endpoint, String deployment, String apiVersion) {
        String root = stripTrailingSlash(requireNotBlank(endpoint, "Azure endpoint 不能为空"));
        if (isBlank(deployment)) {
            throw new IllegalArgumentException("Azure provider 必须配置 deployment（模型部署名）");
        }
        if (isBlank(apiVersion)) {
            throw new IllegalArgumentException("Azure provider 必须配置 api-version");
        }
        return root + "/openai/deployments/" + deployment.trim()
                + CHAT_COMPLETIONS + "?api-version=" + apiVersion.trim();
    }

    /**
     * 把 base-url 归一化成「API 根地址」（<b>不含版本路径</b>）。
     *
     * <p>会去掉：
     * <ul>
     *   <li>尾部的 {@code /}</li>
     *   <li>尾部的 {@code /v1}（<b>可以重复</b>：{@code /v1/v1} 也会被剥干净）</li>
     * </ul>
     *
     * <p>📌 <b>为什么要剥 {@code /v1}</b>：
     * 用户在配置里写 {@code https://api.deepseek.com/v1}（很常见）和
     * 写 {@code https://api.deepseek.com} 表达的是同一个意思，
     * 但拼接时都会再加一段版本路径 → 不归一化就<b>必然</b>拼出双 {@code /v1}。
     * <p>📌 <b>为什么允许重复剥</b>：已经被人手工拼成 {@code .../v1/v1} 的配置
     * 也能自愈，而不是继续报错。
     */
    public static String normalizeRoot(String baseUrl) {
        String s = stripTrailingSlash(requireNotBlank(baseUrl, "LLM base-url 不能为空"));
        while (s.endsWith(OPENAI_V1)) {
            s = stripTrailingSlash(s.substring(0, s.length() - OPENAI_V1.length()));
        }
        return s;
    }

    /**
     * 判断是不是 Azure provider。
     *
     * <p>📌 用 {@code equalsIgnoreCase}：配置里写 {@code Azure} / {@code AZURE} 都认。
     */
    public static boolean isAzure(String provider) {
        return provider != null && "azure".equalsIgnoreCase(provider.trim());
    }

    /**
     * 判断是不是 Anthropic 协议 provider。
     *
     * <p>📌 用 {@code equalsIgnoreCase}：配置里写 {@code Anthropic} / {@code ANTHROPIC} 都认。
     */
    public static boolean isAnthropic(String provider) {
        return provider != null && "anthropic".equalsIgnoreCase(provider.trim());
    }

    /**
     * provider 是否受支持。
     *
     * <p>📌 <b>为什么要显式判支持与否</b>：
     * 之前 {@code provider} 字段被注释成「deepseek, openai, azure」，
     * 但代码里<b>根本没按 provider 分支</b> —— 一律用 OpenAI 兼容的拼法，
     * 于是 Azure 会被拼出一个<b>必然 404 的 URL</b>，然后静默降级成 mock。
     * <p>→ 现在 azure 有真正的分支；<b>其余未知的 provider 明确按 OpenAI 兼容处理</b>
     * （绝大多数自建网关都是这个协议），并在这里给出可读的错误而不是 404。
     *
     * <p>🔴 <b>但这个方法本身也曾经是死代码</b>（2026-11-04 实测）：
     * 它<b>只被单测调用过，运行期没有任何地方调它</b> →
     * provider 拼错（比如 {@code deepseel}）依然能一路畅通，
     * 拼出一个必然 404 的 URL → 静默降级成 mock。
     * <p>→ 现在它被 {@link #validateProvider} 调用，而后者被两个 AI 服务的
     * {@code LLMConfig} 在<b>启动时</b>调用 → 拼错 provider 会<b>启动即失败</b>，
     * 而不是等到用户提问才发现「AI 一直在骗我」。
     */
    public static boolean isSupportedProvider(String provider) {
        if (isBlank(provider)) {
            return false;
        }
        String p = provider.trim().toLowerCase(Locale.ROOT);
        return "azure".equals(p) || "openai".equals(p) || "deepseek".equals(p)
                || "ollama".equals(p) || "qwen".equals(p) || "moonshot".equals(p)
                || "zhipu".equals(p) || "openai-compatible".equals(p)
                || "anthropic".equals(p);
    }

    /**
     * 校验 provider 是否受支持，<b>不受支持就抛异常</b>。
     *
     * <p>📌 <b>为什么要在启动时抛，而不是运行时降级</b>：
     * provider 拼错是一个**配置错误**，不是运行时故障。
     * <ul>
     *   <li>启动时抛 → 容器直接起不来 → 部署流水线立刻发现问题（改对配置重跑就行）</li>
     *   <li>运行时降级 → 用户问了一句才拿到一段 canned 文本，
     *       而且 {@code model} 字段写着真实模型名 → <b>看起来完全正常</b>，
     *       可能上线几个月都没人发现 AI 根本没工作过</li>
     * </ul>
     * → <b>宁可起不来，也不要假装能工作</b>。
     *
     * @param provider provider 名
     * @throws IllegalArgumentException provider 不受支持时
     */
    public static void validateProvider(String provider) {
        if (isSupportedProvider(provider)) {
            return;
        }
        String shown = isBlank(provider) ? "(空)" : provider.trim();
        throw new IllegalArgumentException(
                "不支持的 LLM provider: '" + shown + "'。受支持的取值: "
                        + String.join(", ", SUPPORTED_PROVIDERS)
                        + "（大小写不敏感）。"
                        + "若你用的是自建 OpenAI 兼容网关，请把 provider 设为 'openai-compatible'。");
    }

    /** 受支持的 provider 列表（唯一事实来源） */
    public static final java.util.List<String> SUPPORTED_PROVIDERS = java.util.List.of(
            "deepseek", "openai", "azure", "ollama", "qwen", "moonshot",
            "zhipu", "openai-compatible", "anthropic");

    private static String stripTrailingSlash(String s) {
        String r = s;
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private static String requireNotBlank(String v, String msg) {
        if (isBlank(v)) {
            throw new IllegalArgumentException(msg);
        }
        return v.trim();
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
