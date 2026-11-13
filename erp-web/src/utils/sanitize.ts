/**
 * 安全渲染工具
 * ---------------------------------------------------------------------------
 * 为什么需要这个文件：
 *
 * `erp-web/src/views/ai/Description.vue` 曾经这么写：
 *
 *     <p class="body" v-html="result.body.replace(/\n/g, '<br/>')" />
 *
 * → **存储型 XSS**：`result.body` 是 **LLM 的原始输出**（后端
 *   `ProductDescriptionServiceImpl.optimizeDescription()` 只做了 `trim()`，
 *   一路透传到前端），LLM 输出里只要出现 `<img src=x onerror=...>` 这类内容，
 *   浏览器就会**当 HTML 执行**。
 *
 * 📌 危害比一般 XSS 更大：
 *   ① `body` 会先落库（`descriptionApi.save()` → `t_product_description`），
 *      所以这是**存储型**的 —— 一次生成，全站每个打开该描述页的人都被打。
 *   ② 描述内容本身来自 LLM，而 LLM 的输出**受提示词注入影响**
 *      （商品 SKU / 关键词 / 商品名都是用户可控的输入，会进 prompt）。
 *   ③ 同源 → 偷到的 token 能调 `/api/user/**` 等接口。
 *
 * ---------------------------------------------------------------------------
 * 为什么不用 DOMPurify：
 *
 * ① **不引新依赖** —— `package.json` 只有 10 个运行时依赖，
 *    引一个包 = lockfile 变动 = 供应链面变大 = CI 要重新校验 146 个包的 integrity。
 *    本项目自己踩过「lockfile 全指向国内镜像」的坑（见 code-quality.yml 的注释）。
 * ② **DOMPurify 也不是银弹** —— 它需要 `DOMPurify.sanitize()`，
 *    而漏调一次就等于没防护。**判据放在闸门里**（禁止裸 v-html）比靠人记着调用净化函数可靠。
 *
 * ---------------------------------------------------------------------------
 * 本文件的策略：**白名单式最小渲染**。
 *
 * 描述正文是**纯文本 + 换行**，不需要任何富文本能力：
 *   - `<` `>` `&` `"` `'` → 转义成实体 → 浏览器按**文本**渲染 → 标签不会生效
 *   - `\n` → 唯一的 `<br>`（转义**之后**再插，所以 `<br>` 是我们自己加的、唯一的标签）
 *
 * ✅ 结果：LLM 输出里的一切标签都变成**看得见的文本**，不会被执行。
 * ✅ 且保留了原本「换行显示成换行」的排版效果（原来靠 replace 插 `<br/>`）。
 */

/** HTML 实体转义表（只转义这 5 个就够挡住所有标签与属性注入） */
const HTML_ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

/**
 * 转义 HTML 特殊字符。
 *
 * 📌 `&` 必须**第一个**转义 —— 否则 `&lt;` 里的 `&` 会被二次转义成 `&amp;lt;`
 *    → 页面上显示出 `&lt;` 字面量而不是 `<`。
 */
export function escapeHtml(input: string): string {
  return String(input ?? '').replace(/[&<>"']/g, (ch) => HTML_ESCAPES[ch] ?? ch)
}

/**
 * 把「纯文本 + 换行」渲染成**安全的** HTML 片段（仅 `<br>`）。
 *
 * 用于 `v-html`。因为只放行 `<br>` 这一个标签，
 * ⚠️ 本函数**永远不要**用来渲染用户/LLM 的富文本 —— 它不是通用净化器。
 *
 * @param text 原始文本（通常是 LLM 输出或落库内容）
 * @returns 可安全交给 `v-html` 的字符串
 */
export function renderPlainText(text: string): string {
  // 1) 先转义 → 原文里的所有标签变成实体，不再是标签
  const escaped = escapeHtml(text)
  // 2) 再把换行换成 <br>（此时插入的 <br> 是**我们自己加的**，唯一的标签）
  return escaped.replace(/\r\n|\r|\n/g, '<br/>')
}
