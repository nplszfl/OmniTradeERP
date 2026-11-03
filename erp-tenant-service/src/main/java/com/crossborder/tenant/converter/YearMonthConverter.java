package com.crossborder.tenant.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/**
 * YearMonth ↔ VARCHAR 转换器
 *
 * <p>🔴 <b>为什么必须有这个</b>：Hibernate **6.2.x 不支持 {@link YearMonth}**
 * （整个 hibernate-core 里连 YearMonthType 都没有）。它会把 YearMonth 当成
 * {@code Serializable} 类型处理 → 列类型变成 VARBINARY，读取时用 Java 序列化
 * 反序列化一个 VARCHAR 字符串 → 抛
 * {@code org.hibernate.type.SerializationException: could not deserialize}。</p>
 *
 * <p>症状非常隐蔽：<b>表里没数据时不报错</b>（查不到行就不会反序列化），
 * 一旦有数据，{@code GET /api/tenant/billing/bills} 就 500。</p>
 *
 * <p>所以要么用这个转换器，要么把实体字段改成 String —— 这里选转换器，
 * 保持 Service/Repository 层继续用强类型的 YearMonth。</p>
 */
@Converter(autoApply = false)
public class YearMonthConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.toString(); // yyyy-MM
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String v = dbData.trim();
        // 容错：有的地方可能存成 'yyyy-MM' 以外的形式（比如带日）
        if (v.length() > 7) {
            v = v.substring(0, 7);
        }
        return YearMonth.parse(v);
    }
}
