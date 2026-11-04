package com.crossborder.productdescription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 前端契约的「保存描述」请求
 *
 * <p>前端 {@code erp-web/src/api/ai.ts} 的 {@code descriptionApi.save()} 传的是：
 * <pre>
 * { sku, title, subtitle, body, bulletPoints, language, platform, templateId, generationTime }
 * </pre>
 *
 * <p>📌 <b>为什么用 sku 而不是 productId</b>：
 * 前端商品列表页手里只有 SKU（{@code erp-web/src/views/ai/Description.vue} 就是这么传的），
 * 硬塞 productId 给前端 = 要求前端自己解析雪花 ID，
 * 而雪花 ID 是 19 位，JS 的 Number 装不下 → 前端传回来的 ID 已经被截断 → 匹配不到任何行。</p>
 *
 * <p>📌 <b>校验放在 DTO 上</b>（{@code @NotBlank}），
 * Controller 用 {@code @Valid} 触发 → 缺参数直接 400，而不是写一条空描述进库。</p>
 */
@Data
public class FrontendDescriptionSaveRequest {

    /** 商品 SKU（必填） */
    @NotBlank(message = "商品SKU不能为空")
    private String sku;

    /** 主标题 */
    private String title;

    /** 副标题（落库到 short_title） */
    private String subtitle;

    /** 正文（必填） */
    @NotBlank(message = "描述正文不能为空")
    private String body;

    /** 核心卖点 */
    private List<String> bulletPoints;

    /** 语言（zh / en / ja …），默认 zh */
    private String language = "zh";

    /** 目标平台（amazon / ebay / shopee / general），默认 general */
    private String platform = "general";

    /** 使用的模板ID */
    private String templateId;

    /** 生成耗时（毫秒） */
    private Long generationTime;
}
