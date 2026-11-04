package com.crossborder.productdescription.dto;

import lombok.Data;

/**
 * 前端契约的「保存描述」响应
 *
 * <p>📌 <b>为什么 productId 是 String 而不是 Long</b>：
 * 商品主键是雪花 ID（19 位，例如 {@code 2117546519450398722}），
 * JS 的 Number 只能精确表示到 2^53-1（16 位）→ 用数字返回必然丢精度
 * （本项目已经在这件事上栽过：丢精度后前端把 ID 原样回传 → 匹配不到任何行 →
 * HTTP 200 但删除/更新<b>静默失败</b>）。
 * 这里直接用 String 承载，从源头杜绝这一类问题。</p>
 */
@Data
public class FrontendDescriptionSaveResponse {

    /** 商品ID（字符串！雪花 ID 19 位，用数字会丢精度） */
    private String productId;

    /** 商品名称 */
    private String productName;

    /** 是否保存成功（真正落库后才是 true） */
    private Boolean saved;

    /** 保存后的版本号（同一 sku + 语言 + 平台重复保存会递增） */
    private Integer versionNumber;
}
