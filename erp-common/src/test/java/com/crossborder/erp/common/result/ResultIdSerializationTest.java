package com.crossborder.erp.common.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「雪花 ID 必须以字符串输出」的**出口层**测试。
 *
 * <p>🔴 <b>为什么这是必须锁死的：</b>本项目 45 个实体用雪花算法生成 19 位主键
 * （如 {@code 2117546519450398722}），JS 的 {@code Number} 只能精确表示
 * {@code 2^53-1}（16 位）→ {@code JSON.parse} <b>必然丢精度</b>（实测差 22）
 * → 前端把丢失精度的 id 原样发回后端 → 后端匹配不到任何行
 * → <b>HTTP 200，删除/更新静默失败</b>。</p>
 *
 * <p>📌 <b>本类锁的是「出口」这一层</b>（{@code erp-common} 的
 * {@code CommonJacksonAutoConfigurationTest} 锁的是「bean 属性」那一层）。
 * 两者都必须锁 —— 出口层有个大坑：</p>
 *
 * <ol>
 *   <li><b>根级标量</b>（Controller 直接 {@code return id;}）走
 *       {@code LongSerializer}，<b>不经过</b> {@code BeanSerializerModifier}
 *       → 自动装配完全管不到它。实测：{@code @JsonSerialize} 方法级注解也无效。</li>
 *   <li><b>{@code Result<T>.data}</b> 的静态类型是 {@code Object}（泛型擦除），
 *       Jackson <b>分不出「这是 id」还是「这是计数」</b>。</li>
 * </ol>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@DisplayName("雪花 ID 出口层（根级标量 + Result.data）")
class ResultIdSerializationTest {

    /** 典型的雪花 ID —— 超过 JS 的 2^53 安全整数范围 */
    private static final long SNOWFLAKE = 2117546519450398722L;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Result.successId 的 data 必须是字符串 —— 否则前端拿到的 id 已被截断")
    void successIdSerializesAsString() throws Exception {
        Result<String> r = Result.successId(SNOWFLAKE);

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"data\":\"2117546519450398722\"");
        // 📌 关键断言：不能是数字形态
        assertThat(json).doesNotContain("\"data\":2117546519450398722");
    }

    @Test
    @DisplayName("Result.successId(null) → data 为 null，不能变成字符串 \"null\"")
    void successIdWithNull() throws Exception {
        Result<String> r = Result.successId(null);

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"data\":null");
    }

    @Test
    @DisplayName("Result.success(计数) 不受影响 —— data 仍是数字")
    void successWithCountUnaffected() throws Exception {
        Result<Integer> r = Result.success(42);

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"data\":42");
    }

    @Test
    @DisplayName("Result.success(实体) 不受影响 —— data 是对象")
    void successWithEntityUnaffected() throws Exception {
        Result<Holder> r = Result.success(new Holder("x", 7));

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"name\":\"x\"");
        assertThat(json).contains("\"count\":7");
    }

    @Test
    @DisplayName("🔴 根级 Long 标量不走 BeanSerializerModifier —— 这就是必须改返回类型的原因")
    void rootLevelLongIsNotCoveredByBeanRule() throws Exception {
        // 这不是断言「应该输出字符串」，而是**记录一个必须知道的事实**：
        // 根级标量走的是 LongSerializer，BeanSerializerModifier 管不到。
        // 📌 所以 Controller 不能 `return id;`（Long），必须显式转字符串。
        String json = mapper.writeValueAsString(SNOWFLAKE);

        assertThat(json).isEqualTo("2117546519450398722");
        // ⚠️ 这个数字形态一旦进到 JS 就会丢精度 —— 见下方 round-trip 断言
    }

    @Test
    @DisplayName("字符串形式的 id 能被精确反序列化回 Long（前端原样回传的场景）")
    void stringIdDeserializesBackToExactLong() throws Exception {
        String json = "{\"code\":200,\"message\":\"success\",\"data\":\"2117546519450398722\"}";

        Result<Long> r = mapper.readValue(json, new TypeReference<Result<Long>>() {});

        assertThat(r.getData()).isEqualTo(SNOWFLAKE);
    }

    @Test
    @DisplayName("Feign 端声明 Result<Long> 依然能用 —— 字符串自动转回精确 Long")
    void feignClientCanStillReadLong() throws Exception {
        // 📌 跨服务契约：服务端发字符串，Feign 端声明 Result<Long>，
        //    Jackson 自动转换且**精确**（已实测）。
        String serverJson = mapper.writeValueAsString(Result.successId(SNOWFLAKE));

        Result<Long> clientSide = mapper.readValue(serverJson, new TypeReference<Result<Long>>() {});

        assertThat(clientSide.getData()).isEqualTo(SNOWFLAKE);
        assertThat(clientSide.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("🔴 截断实证：数字形态的 id 确实无法精确往返")
    void numericFormLosesPrecisionInJs() throws Exception {
        String asNumber = mapper.writeValueAsString(SNOWFLAKE);

        // 📌 模拟 JS 的 JSON.parse：JSON 数字 → IEEE754 double（53 位有效数字）
        double parsed = Double.parseDouble(asNumber);

        // 📌 关键事实：**double 本身只能存下 2117546519450398720**，
        //    原始的 ...722 已经不可区分了 —— 这就是精度丢失。
        //    （注意：解析出来的 double 与 (double)SNOWFLAKE 是同一个 double，
        //      损坏发生在「十进制表示 ↔ double」的往返上，不是 bits 层面。）
        assertThat((long) parsed).isEqualTo(2117546519450398720L);
        assertThat((long) parsed).isNotEqualTo(SNOWFLAKE);

        // 📌 JS 的 String(Number) 用**最短往返表示**，显示成 ...700；
        //    Java 的 (long) 截断显示成 ...720 —— 两者都 ≠ 原值 ...722。
        //    同一个 double，不同的最短表示算法。
        assertThat(SNOWFLAKE - (long) parsed).isEqualTo(2L);
    }

    static class Holder {
        private String name;
        private Integer count;

        Holder(String name, Integer count) {
            this.name = name;
            this.count = count;
        }

        public String getName() { return name; }
        public Integer getCount() { return count; }
    }
}
