package com.crossborder.erp.llm;

import java.util.Locale;

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
        if (isAzure(provider)) {
            return azureChatCompletionsUrl(baseUrl, deployment, apiVersion);
        }
        return openAiCompatibleUrl(baseUrl);
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
     * provider 是否受支持。
     *
     * <p>📌 <b>为什么要显式判支持与否</b>：
     * 之前 {@code provider} 字段被注释成「deepseek, openai, azure」，
     * 但代码里<b>根本没按 provider 分支</b> —— 一律用 OpenAI 兼容的拼法，
     * 于是 Azure 会被拼出一个<b>必然 404 的 URL</b>，然后静默降级成 mock。
     * <p>→ 现在 azure 有真正的分支；<b>其余未知的 provider 明确按 OpenAI 兼容处理</b>
     * （绝大多数自建网关都是这个协议），并在这里给出可读的错误而不是 404。
     */
    public static boolean isSupportedProvider(String provider) {
        if (isBlank(provider)) {
            return false;
        }
        String p = provider.trim().toLowerCase(Locale.ROOT);
        return "azure".equals(p) || "openai".equals(p) || "deepseek".equals(p)
                || "ollama".equals(p) || "qwen".equals(p) || "moonshot".equals(p)
                || "zhipu".equals(p) || "openai-compatible".equals(p);
    }

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
