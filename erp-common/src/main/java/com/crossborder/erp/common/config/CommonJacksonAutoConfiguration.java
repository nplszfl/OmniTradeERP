package com.crossborder.erp.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.List;

/**
 * 通用 Jackson 自动装配 —— <b>把 Long 类型的主键 / 外键序列化成字符串</b>。
 *
 * <p>🔴 <b>这个类存在的唯一原因，是修一个「静默失效」型全项目级缺陷。</b></p>
 *
 * <p><b>缺陷本体：</b>本项目 45 个实体用 MyBatis-Plus 的
 * {@code IdType.ASSIGN_ID}（雪花算法）生成主键 —— 19 位十进制，例如：</p>
 * <pre>
 * 2117546519450398722
 * </pre>
 *
 * <p>而 JavaScript 的 {@code Number} 只能精确表示
 * {@code 2^53-1 = 9007199254740991}（16 位）。19 位数字在
 * {@code JSON.parse} 之后<b>必然丢精度</b>：</p>
 * <pre>
 * 后端返回 : 2117546519450398722
 * JS 解析 : 2117546519450398700      ← 差 22
 * </pre>
 *
 * <p><b>后果（比报错更糟）：</b>前端把丢失精度的 id 原样发回后端
 * （{@code DELETE /customer/2117546519450398700}），
 * 后端 {@code removeById} 匹配不到任何行 → <b>HTTP 200，删除静默失败</b>。
 * 用户点了删除、UI 提示成功，数据纹丝不动。</p>
 *
 * <p>而种子数据用的是小 id（9001 / 9101 / 9404…），<b>看起来一切正常</b>
 * —— 只有「新建 → 编辑/删除」这条最常用的流程会炸。</p>
 *
 * <p>✅ <b>修复方式（业界标准）</b>：把 id 序列化成字符串。
 * JSON 里的 {@code "2117546519450398722"} 是精确的，JS 拿到的是字符串，
 * 原样回传后端时 {@code @PathVariable Long id} / {@code @RequestBody Long id}
 * 都能精确还原。</p>
 *
 * <p>📌 <b>为什么只处理 id 样字段，不全局处理所有 Long</b>：</p>
 * <ul>
 *   <li>{@code IPage.total} 是 Long → 必须保持数字，否则 Element Plus 分页组件
 *       的 {@code total} prop 类型不匹配</li>
 *   <li>{@code apiCalls} / {@code storageBytes} / {@code monthlySearches} 等
 *       计数类字段同理</li>
 * </ul>
 * <p>因此规则是：<b>属性名为 {@code id} 或以 {@code Id} 结尾，且类型是
 * {@code Long}/{@code long}</b>。已核对全项目 122 个 {@code Long} 字段，
 * 非 id 的计数类字段（{@code total} / {@code apiCalls} / {@code storageBytes} /
 * {@code monthlySearches} / {@code upperBound} / {@code lowerBound} …）
 * 全部不匹配该规则。</p>
 *
 * <p>📌 <b>跨服务兼容</b>：Feign 反序列化时 Jackson 会把 JSON 字符串
 * {@code "123"} 自动转成 {@code Long 123}，不需要任何额外处理。</p>
 *
 * <p>📌 <b>放在 erp-common 的自动装配里</b>，而不是逐个实体加
 * {@code @JsonSerialize(using = ToStringSerializer.class)} ——
 * 45 个实体逐个加注解，漏一个就是一个坑；而自动装配对所有服务一次性生效
 * （各服务的 {@code @SpringBootApplication} 都在自己的子包下，
 * 组件扫描<b>不包含</b> {@code com.crossborder.erp.common}）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@AutoConfiguration
@ConditionalOnClass(com.fasterxml.jackson.databind.ObjectMapper.class)
public class CommonJacksonAutoConfiguration {

    /**
     * id 样属性名：{@code id} 本身，或以 {@code Id} 结尾（customerId / orderId …）
     */
    private static boolean isIdLike(String name) {
        return "id".equals(name) || (name != null && name.length() > 2 && name.endsWith("Id"));
    }

    /**
     * 属性类型是不是 {@code Long} / {@code long}
     */
    private static boolean isLongType(BeanPropertyWriter writer) {
        Class<?> raw = writer.getType().getRawClass();
        return raw == Long.class || raw == Long.TYPE;
    }

    @Bean
    public SimpleModule erpLongIdAsStringModule() {
        SimpleModule module = new SimpleModule("erp-long-id-as-string");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(
                    SerializationConfig config,
                    BeanDescription beanDesc,
                    List<BeanPropertyWriter> beanProperties) {
                for (BeanPropertyWriter writer : beanProperties) {
                    if (isIdLike(writer.getName()) && isLongType(writer)) {
                        // 📌 assignSerializer 会把同一个 serializer 也设成 nullSerializer，
                        //    所以这个 serializer **必须自己处理 null**（否则 NPE）。
                        writer.assignSerializer(LongIdAsStringSerializer.INSTANCE);
                    }
                }
                return beanProperties;
            }
        });
        return module;
    }

    /**
     * Long → String 序列化器（自己处理 null，避免 NPE）
     */
    public static final class LongIdAsStringSerializer extends JsonSerializer<Object> {

        public static final LongIdAsStringSerializer INSTANCE = new LongIdAsStringSerializer();

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(value.toString());
        }
    }
}
