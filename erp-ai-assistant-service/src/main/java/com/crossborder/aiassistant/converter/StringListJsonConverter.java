package com.crossborder.aiassistant.converter;

import com.alibaba.fastjson2.JSON;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * List&lt;String&gt; ↔ JSON 字符串 转换器
 *
 * 📌 为什么需要它：
 *    `knowledge_base.tags` 列在 MySQL 里是 **JSON** 类型，
 *    而 Java 侧想用 `List&lt;String&gt;`。
 *    直接把 List 映射到 JSON 列会报
 *    "Invalid JSON text: 'xxx'"（MySQL 对 JSON 列做严格校验）。
 *
 * 📌 用 fastjson2 而不是 Jackson：
 *    本模块的 RAGService 里明确注释过「手写序列化避免循环依赖」。
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = JSON.parseArray(dbData, String.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            // 脏数据不该让整个查询失败
            return List.of();
        }
    }
}
