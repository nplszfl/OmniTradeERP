package com.crossborder.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用 Jackson 自动装配测试 —— 锁死「Long 型 id 必须以字符串输出」。
 *
 * <p>🔴 <b>为什么这是必须锁死的：</b>本项目 45 个实体用雪花算法生成 19 位主键，
 * JS 的 {@code Number} 只能精确表示 16 位 → {@code JSON.parse} 必然丢精度
 * → 前端回传丢失精度的 id → 后端删除/更新<b>静默失败</b>（HTTP 200 但数据没动）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@DisplayName("通用 Jackson 自动装配（Long id 序列化为字符串）")
class CommonJacksonAutoConfigurationTest {

    /** 典型的雪花 ID —— 超过 JS 的 2^53 安全整数范围 */
    private static final long SNOWFLAKE = 2117546519450398722L;

    static class SampleDto {
        private Long id;
        private Long customerId;
        private Long total;
        private Long apiCalls;
        private Long storageBytes;
        private Integer count;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
        public Long getApiCalls() { return apiCalls; }
        public void setApiCalls(Long apiCalls) { this.apiCalls = apiCalls; }
        public Long getStorageBytes() { return storageBytes; }
        public void setStorageBytes(Long storageBytes) { this.storageBytes = storageBytes; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new CommonJacksonAutoConfiguration().erpLongIdAsStringModule());
    }

    @Test
    @DisplayName("雪花 ID 必须原样输出为字符串 —— 不能被 JS 截断")
    void snowflakeIdIsSerializedAsExactString() throws Exception {
        SampleDto dto = new SampleDto();
        dto.setId(SNOWFLAKE);

        String json = mapper().writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"2117546519450398722\"");
        // 📌 关键断言：不能是数字形态，否则 JS 解析就丢精度
        assertThat(json).doesNotContain("\"id\":2117546519450398722");
    }

    @Test
    @DisplayName("外键（xxxId）同样要字符串化 —— 它存的也是雪花 ID")
    void foreignKeyIdIsSerializedAsString() throws Exception {
        SampleDto dto = new SampleDto();
        dto.setCustomerId(SNOWFLAKE);

        String json = mapper().writeValueAsString(dto);

        assertThat(json).contains("\"customerId\":\"2117546519450398722\"");
    }

    @Test
    @DisplayName("计数类 Long 字段保持数字 —— 分页组件的 total 必须是数字")
    void countLikeLongsStayNumbers() throws Exception {
        SampleDto dto = new SampleDto();
        dto.setTotal(12L);
        dto.setApiCalls(345L);
        dto.setStorageBytes(1024L);

        String json = mapper().writeValueAsString(dto);

        assertThat(json).contains("\"total\":12");
        assertThat(json).contains("\"apiCalls\":345");
        assertThat(json).contains("\"storageBytes\":1024");
    }

    @Test
    @DisplayName("Integer 字段不受影响")
    void integerFieldsUnaffected() throws Exception {
        SampleDto dto = new SampleDto();
        dto.setCount(7);

        assertThat(mapper().writeValueAsString(dto)).contains("\"count\":7");
    }

    @Test
    @DisplayName("id 为 null 时输出 null，不能变成字符串 \"null\"")
    void nullIdStaysNull() throws Exception {
        SampleDto dto = new SampleDto();
        dto.setId(null);

        String json = mapper().writeValueAsString(dto);

        assertThat(json).contains("\"id\":null");
        assertThat(json).doesNotContain("\"null\"");
    }

    @Test
    @DisplayName("反序列化：JSON 字符串能精确还原成 Long（前端原样回传的场景）")
    void stringDeserializesBackToExactLong() throws Exception {
        String json = "{\"id\":\"2117546519450398722\",\"customerId\":\"2117546519450398700\"}";

        SampleDto dto = mapper().readValue(json, SampleDto.class);

        assertThat(dto.getId()).isEqualTo(SNOWFLAKE);
        assertThat(dto.getCustomerId()).isEqualTo(2117546519450398700L);
    }

    @Test
    @DisplayName("自动装配确实注册了 Module（否则各服务都不会生效）")
    void moduleIsRegisteredInContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonJacksonAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            com.fasterxml.jackson.databind.Module.class);
                });
    }

    @Test
    @DisplayName("自动装配清单里登记了该配置类（否则其它服务根本不会加载）")
    void isRegisteredInAutoConfigurationImports() throws Exception {
        String imports;
        try (var in = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).as("AutoConfiguration.imports 必须存在").isNotNull();
            imports = new String(in.readAllBytes());
        }
        assertThat(imports).contains(CommonJacksonAutoConfiguration.class.getName());
    }
}
