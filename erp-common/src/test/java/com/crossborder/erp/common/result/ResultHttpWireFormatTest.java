package com.crossborder.erp.common.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 <b>「雪花 ID 在 HTTP 线上到底长什么样」的端到端测试。</b>
 *
 * <p>为什么必须是<b>真起 HTTP</b> 才能测：</p>
 * <p>前面踩过的三个坑，全部只在<b>真实 HTTP 响应体</b>上才暴露：</p>
 * <ol>
 *   <li><b>根级 Long</b> 走 {@code LongSerializer}，不经 {@code BeanSerializerModifier}
 *       → 输出数字 → JS 丢精度。</li>
 *   <li><b>改成裸 String 也不行</b>：Spring 的 {@code StringHttpMessageConverter}
 *       <b>先于 Jackson</b> 接管 String 返回值 → 输出<b>无引号</b>的裸文本
 *       → 前端 {@code JSON.parse("2117...")} → <b>又是数字</b> → 照样丢精度。
 *       📌 加 {@code produces="application/json"} 也<b>没用</b>：
 *       Content-Type 变成 application/json，但 body 仍是裸数字（实测）。</li>
 *   <li>唯一可靠的做法：{@code Result<String>} —— 它是 bean，
 *       走 Jackson → 输出<b>带引号</b>的字符串 → JS 拿到的是 string。</li>
 * </ol>
 *
 * <p>📌 这些都<b>不是</b> {@code @JsonSerialize} 能解决的：
 * 方法级注解对根级标量无效（Jackson 不查根级值的方法注解）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@DisplayName("雪花 ID 的 HTTP 线上形态（真起 MockMvc）")
class ResultHttpWireFormatTest {

    private static final long SNOWFLAKE = 2117546519450398722L;

    /** 对照组：返回裸 Long（缺陷版本） */
    @RestController
    static class BareLongController {
        @PostMapping("/bare-long")
        public Long bareLong() {
            return SNOWFLAKE;
        }
    }

    /** 对照组：返回裸 String（同样是缺陷版本 —— 无引号裸文本） */
    @RestController
    static class BareStringController {
        @PostMapping("/bare-string")
        public String bareString() {
            return String.valueOf(SNOWFLAKE);
        }
    }

    /** 对照组：返回 Result<String>（正确版本） */
    @RestController
    static class WrappedController {
        @PostMapping("/wrapped")
        public Result<String> wrapped() {
            return Result.successId(SNOWFLAKE);
        }
    }

    private String doPost(MockMvc mvc, String url) throws Exception {
        return mvc.perform(post(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("🔴 裸 Long → 响应体是数字 → JS JSON.parse 后丢精度")
    void bareLongIsANumber() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BareLongController()).build();

        String body = doPost(mvc, "/bare-long");

        org.assertj.core.api.Assertions.assertThat(body)
                .as("裸 Long 必须被识别为缺陷：body 是裸数字")
                .isEqualTo("2117546519450398722");
        org.assertj.core.api.Assertions.assertThat(body)
                .as("没有引号 → JSON.parse 后是 number → 精度丢失")
                .doesNotStartWith("\"");
    }

    @Test
    @DisplayName("🔴 裸 String → 仍是裸文本（无引号）→ JSON.parse 后还是 number")
    void bareStringIsStillUnquoted() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BareStringController()).build();

        String body = doPost(mvc, "/bare-string");

        org.assertj.core.api.Assertions.assertThat(body)
                .as("裸 String 被 StringHttpMessageConverter 原样输出，没有引号")
                .isEqualTo("2117546519450398722");
        org.assertj.core.api.Assertions.assertThat(body)
                .as("⚠️ 这就是「只改成 String 就以为修好了」的陷阱")
                .doesNotStartWith("\"");
    }

    @Test
    @DisplayName("✅ Result<String> → 响应体是带引号的字符串 → JS 拿到 string，精度无损")
    void wrappedIsAQuotedString() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WrappedController()).build();

        String body = doPost(mvc, "/wrapped");

        org.assertj.core.api.Assertions.assertThat(body)
                .as("必须是带引号的 JSON 字符串")
                .contains("\"data\":\"2117546519450398722\"");
    }

    @Test
    @DisplayName("✅ 三种写法的 JS 解析结果对比 —— 只有 Result<String> 不丢精度")
    void jsParseComparison() throws Exception {
        MockMvc bareLongMvc = MockMvcBuilders.standaloneSetup(new BareLongController()).build();
        MockMvc bareStrMvc = MockMvcBuilders.standaloneSetup(new BareStringController()).build();
        MockMvc wrappedMvc = MockMvcBuilders.standaloneSetup(new WrappedController()).build();

        String bareLong = doPost(bareLongMvc, "/bare-long");
        String bareStr = doPost(bareStrMvc, "/bare-string");
        String wrapped = doPost(wrappedMvc, "/wrapped");

        // 模拟 JS JSON.parse（数字 → double）
        ObjectMapper mapper = new ObjectMapper();
        Object parsedBare = mapper.readValue(bareLong, Object.class);
        Object parsedWrapped = mapper.readValue(wrapped, java.util.Map.class);

        org.assertj.core.api.Assertions.assertThat(parsedBare)
                .as("裸 Long 解析出来是数字")
                .isInstanceOf(Number.class);
        org.assertj.core.api.Assertions.assertThat(((java.util.Map<?, ?>) parsedWrapped).get("data"))
                .as("Result<String> 的 data 解析出来是字符串")
                .isInstanceOf(String.class);

        // 关键：字符串形态能精确往返，数字形态不能
        String dataStr = String.valueOf(((java.util.Map<?, ?>) parsedWrapped).get("data"));
        org.assertj.core.api.Assertions.assertThat(dataStr)
                .as("字符串形态原样保留 19 位")
                .isEqualTo("2117546519450398722");
    }

    @Test
    @DisplayName("✅ Result 走 Jackson（application/json），裸 String 走 text/plain")
    void contentTypeDiffers() throws Exception {
        MockMvc bareStrMvc = MockMvcBuilders.standaloneSetup(new BareStringController()).build();
        MockMvc wrappedMvc = MockMvcBuilders.standaloneSetup(new WrappedController()).build();

        String ct1 = bareStrMvc.perform(post("/bare-string"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentType();
        String ct2 = wrappedMvc.perform(post("/wrapped"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentType();

        org.assertj.core.api.Assertions.assertThat(ct1)
                .as("裸 String → text/plain（Spring 的 StringHttpMessageConverter 抢先接管）")
                .contains("text/plain");
        org.assertj.core.api.Assertions.assertThat(ct2)
                .as("Result → application/json（走 Jackson，才有引号）")
                .contains(MediaType.APPLICATION_JSON_VALUE);
    }
}
