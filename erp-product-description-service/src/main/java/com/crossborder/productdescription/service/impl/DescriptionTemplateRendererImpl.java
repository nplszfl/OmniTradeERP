package com.crossborder.productdescription.service.impl;

import com.alibaba.fastjson2.JSON;
import com.crossborder.productdescription.entity.DescriptionTemplate;
import com.crossborder.productdescription.service.DescriptionTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 描述模板渲染器实现
 *
 * <p>📌 <b>2026-11-24 新增</b>。</p>
 *
 * <p>📌 <b>占位符语法</b>：模板正文里写 {@code {产品名称}} / {@code {产品特性}}，
 * 渲染时替换成真实值。<b>没提供值的占位符原样保留</b>
 * —— 留着 {@code {产品名称}} 反而能让 LLM 看出「这里本该填什么」；
 * 编一个假值填进去才是撒谎（规则 72）。</p>
 *
 * <p>📌 <b>用 fastjson2 而不是 Jackson</b>：本模块的 pom 已引入 fastjson2，
 * 且 {@code erp-ai-assistant-service} 的 {@code StringListJsonConverter}
 * 就是用它做 JSON 转换的（避免跨模块依赖差异）。</p>
 */
@Slf4j
@Component
public class DescriptionTemplateRendererImpl implements DescriptionTemplateRenderer {

    /** 占位符：{@code {变量名}} —— 允许中文、字母、数字、下划线 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}\\s]{1,40})}");

    @Override
    public String render(DescriptionTemplate template, Map<String, String> variables) {
        if (template == null || template.getTemplateContent() == null
                || template.getTemplateContent().isBlank()) {
            return "";
        }
        String content = template.getTemplateContent();
        if (variables == null || variables.isEmpty()) {
            return content;
        }
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            // 📌 **没有对应取值 → 原样保留占位符**（不编）
            String replacement = (value == null || value.isBlank()) ? matcher.group(0) : value;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    @Override
    public List<String> parseVariables(DescriptionTemplate template) {
        if (template == null || template.getVariables() == null
                || template.getVariables().isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = JSON.parseArray(template.getVariables(), String.class);
            if (parsed == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>(parsed.size());
            for (String v : parsed) {
                if (v != null && !v.isBlank()) {
                    out.add(v);
                }
            }
            return out;
        } catch (Exception e) {
            // 📌 脏数据不该让整个查询失败 —— 但**必须留痕**（否则「模板变量解析不出来」没人知道）
            log.warn("解析模板变量失败（templateId={}）：{}",
                    template.getTemplateId(), e.getMessage());
            return List.of();
        }
    }
}
