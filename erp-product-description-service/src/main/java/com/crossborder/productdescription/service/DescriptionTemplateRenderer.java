package com.crossborder.productdescription.service;

import com.crossborder.productdescription.entity.DescriptionTemplate;

import java.util.List;
import java.util.Map;

/**
 * 描述模板渲染器
 *
 * <p>📌 <b>2026-11-24 新增</b>：把模板正文里的 {@code {变量名}} 占位符
 * 替换成<b>真实商品事实</b>。</p>
 *
 * <p>🔴 <b>修它之前，模板正文从来没进过 prompt</b>：
 * {@code DescriptionGenerationRequest.useTemplate} / {@code templateId}
 * 是两个<b>死参数</b> —— 传了也没人用，运营在后台改模板对生成结果零影响。</p>
 */
public interface DescriptionTemplateRenderer {

    /**
     * 把模板正文渲染成一段「给 LLM 的写作要求」
     *
     * @param template 模板（{@code null} → 返回空字符串，**不编**）
     * @param variables 变量取值（键是占位符名，值是替换文本；可为 {@code null}）
     * @return 渲染后的文本（模板为 {@code null} → 空字符串）
     */
    String render(DescriptionTemplate template, Map<String, String> variables);

    /**
     * 解析模板声明的变量名列表
     *
     * <p>📌 {@code description_template.variables} 列是 MySQL {@code JSON} 数组
     * （如 {@code ["产品名称","产品特性"]}）。</p>
     *
     * @param template 模板（{@code null} → 空列表）
     * @return 变量名列表（解析不出来 → 空列表，**不编**）
     */
    List<String> parseVariables(DescriptionTemplate template);
}
