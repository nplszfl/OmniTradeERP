package com.crossborder.erp.platform.feign;

import com.crossborder.erp.common.result.Result;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 🔴 <b>Feign 解码端的真实契约测试</b> —— 跨服务传 id 的最后一环。
 *
 * <p><b>为什么必须真起解码器</b>：本项目服务端把主键改成字符串输出后，
 * Feign 客户端仍声明 {@code Result<Long>}。这个组合能不能 work，
 * <b>取决于 Jackson 在反序列化时能否把 JSON 字符串 {@code "2117..."}
 * 精确还原成 {@code Long}</b>。</p>
 *
 * <p>📌 <b>为什么不能用 mock 代替</b>：
 * {@code OrderSyncScheduleTest} 里写的是
 * {@code when(orderServiceClient.createOrder(any())).thenReturn(Result.success(123L))}
 * —— 它<b>直接构造了一个 Java 对象</b>，完全绕过了 JSON 编解码。
 * 真实链路上服务端发的是 <b>JSON 字符串</b>，
 * 客户端拿到的 {@code Result<Long>.data} 可能是 {@code String}，
 * {@code getData()} 就会抛 {@code ClassCastException: String cannot be cast to Long}。</p>
 *
 * <p>📌 这就是「mock 不过滤 = 测试假绿」的又一次形态：
 * <b>测试构造的对象和生产环境流动的对象不是同一个东西。</b></p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@DisplayName("Feign 解码：服务端发字符串 id，客户端声明 Result<Long>")
class OrderServiceClientDecodeTest {

    /** 典型的雪花 ID —— 19 位，超出 JS 安全整数范围 */
    private static final long SNOWFLAKE = 2117546519450398722L;

    /**
     * 订单服务现在**真实**返回的响应体。
     *
     * <p>📌 注意 {@code "data"} 是<b>带引号的字符串</b> —— 这是
     * {@code Result.successId()} 的输出形态。</p>
     */
    private static final String SERVER_RESPONSE_BODY = """
            {"code":200,"message":"success","data":"2117546519450398722","timestamp":1793699427534}
            """;

    private SpringDecoder decoder() {
        List<HttpMessageConverter<?>> converters = List.of(new MappingJackson2HttpMessageConverter());
        HttpMessageConverters httpMessageConverters = new HttpMessageConverters(converters);
        // 📌 SpringDecoder 需要 ObjectFactory<HttpMessageConverters>
        return new SpringDecoder(() -> httpMessageConverters);
    }

    private Response feignResponse(String body) {
        Map<String, Collection<String>> headers = new HashMap<>();
        headers.put("Content-Type", List.of(MediaType.APPLICATION_JSON_VALUE));
        return Response.builder()
                .status(200)
                .reason("OK")
                .headers(headers)
                .request(Request.create(Request.HttpMethod.POST,
                        "http://erp-order-service/order/create",
                        Map.of(), null, StandardCharsets.UTF_8, null))
                .body(body.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private Type createOrderReturnType() throws Exception {
        return OrderServiceClient.class
                .getMethod("createOrder", OrderServiceClient.OrderCreateRequest.class)
                .getGenericReturnType();
    }

    @Test
    @DisplayName("🔴 真实 Feign 解码：data 是字符串时，Result<Long> 必须能精确还原成 Long")
    void stringDataDecodesToExactLong() throws Exception {
        Type targetType = createOrderReturnType();
        assertThat(targetType.getTypeName())
                .as("Feign 客户端声明的是 Result<Long>")
                .contains("Result")
                .contains("Long");

        Object decoded = decoder().decode(feignResponse(SERVER_RESPONSE_BODY), targetType);

        assertThat(decoded).isInstanceOf(Result.class);
        @SuppressWarnings("unchecked")
        Result<Long> result = (Result<Long>) decoded;

        // 📌 关键断言：不能是 String（否则 getData() 在业务代码里会 ClassCastException）
        assertThat(result.getData())
                .as("data 必须被还原成 Long，不能是 String")
                .isInstanceOf(Long.class);
        // 📌 关键断言：必须精确
        assertThat(result.getData()).isEqualTo(SNOWFLAKE);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("🔴 业务代码真实用法：Long orderId = createdResult.getData() 不能抛 ClassCastException")
    void businessUsageDoesNotThrowClassCastException() throws Exception {
        Type targetType = createOrderReturnType();

        @SuppressWarnings("unchecked")
        Result<Long> createdResult =
                (Result<Long>) decoder().decode(feignResponse(SERVER_RESPONSE_BODY), targetType);

        // 📌 这就是 OrderSyncSchedule 里的真实写法
        Long orderId = createdResult == null ? null : createdResult.getData();

        assertThatCode(() -> {
            Long x = createdResult.getData();
            assertThat(x).isNotNull();
        }).doesNotThrowAnyException();
        assertThat(orderId).isEqualTo(SNOWFLAKE);
    }

    @Test
    @DisplayName("📌 对照：数字形态在 Java 侧也是精确的 —— 弱的是浏览器那一端")
    void numericDataAlsoDecodes() throws Exception {
        String numericBody = """
                {"code":200,"message":"success","data":2117546519450398722,"timestamp":1}
                """;

        Object decoded = decoder().decode(feignResponse(numericBody), createOrderReturnType());

        @SuppressWarnings("unchecked")
        Result<Long> r = (Result<Long>) decoded;
        // 📌 数字形态在 Java 侧也是精确的 —— 问题出在**浏览器**那一端，
        //    所以「服务端改成字符串」才是正解（浏览器才是弱的那个环节）。
        assertThat(r.getData()).isEqualTo(SNOWFLAKE);
    }

    @Test
    @DisplayName("📌 客户端拿到的 id 必须能原样回传（字符串 → Long 往返无损）")
    void decodedIdCanBeSentBack() throws Exception {
        Type targetType = createOrderReturnType();
        @SuppressWarnings("unchecked")
        Result<Long> result =
                (Result<Long>) decoder().decode(feignResponse(SERVER_RESPONSE_BODY), targetType);

        Long orderId = result.getData();

        // 📌 服务端把 id 当路径参数回传时，必须还是同一个值
        assertThat(orderId.toString()).isEqualTo("2117546519450398722");
    }
}
