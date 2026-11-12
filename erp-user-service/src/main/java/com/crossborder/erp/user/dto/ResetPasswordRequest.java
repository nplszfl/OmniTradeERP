package com.crossborder.erp.user.dto;

import lombok.Data;

/**
 * 重置密码请求体
 *
 * <p>📌 <b>为什么口令必须走 body，不能走 URL 查询串</b>（2026-11-12 修，规则 14 / 140 同族）：
 * <pre>
 *   ❌ POST /api/user/1/reset-password?defaultPassword=Secret123
 *   ✅ POST /api/user/1/reset-password   body: {"defaultPassword":"Secret123"}
 * </pre>
 * nginx / ingress / 网关的访问日志格式里有 {@code $request} = <b>完整请求行（含 query string）</b>
 * → 口令走 query → <b>明文口令进访问日志</b>（也可能进浏览器历史、代理日志、审计日志）。
 * 📌 而 POST 的 <b>body 不会</b>进访问日志（除非应用自己打出来）。
 * 📌 另：{@code @RequestParam} 会把参数<b>暴露在 URL</b>上，
 * 也更容易被复制链接、日志采集、Referer 头带出去。
 * </p>
 *
 * <p>📌 <b>字段可选</b>：不传 = 重置为默认口令（这才是前端目前的用法，
 * 见 {@code erp-web/src/api/user.ts} 的 {@code resetPassword}）→ 契约保持兼容。</p>
 *
 * @author 火球鼠
 * @since 2026-11-12
 */
@Data
public class ResetPasswordRequest {

    /**
     * 指定的新口令；不传（null / 空串）则重置为默认口令
     *
     * <p>📌 口令只在<b>请求体</b>里出现 —— 绝不能进 URL。</p>
     */
    private String defaultPassword;
}
