package com.crossborder.erp.common.exception;

import com.crossborder.erp.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器测试
 *
 * <p>锁死两件事：
 * 1. 响应体必须是统一的 {@link Result}（code/message/data），不能是 Spring 默认错误体；
 * 2. HTTP 状态码必须与业务语义一致（4xx/5xx），
 *    否则前端 axios 的 error 拦截器不会触发，错误被静默吞掉。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@DisplayName("全局异常处理器")
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("业务异常返回 400 + Result 响应体")
    void bizExceptionReturnsResultEnvelope() throws Exception {
        mockMvc.perform(get("/test/biz"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("业务规则不满足"));
    }

    @Test
    @DisplayName("业务异常码落在 4xx/5xx 区间时直接作为 HTTP 状态码")
    void bizExceptionCodeMapsToHttpStatus() throws Exception {
        mockMvc.perform(get("/test/biz-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    @DisplayName("无消息的系统异常返回 500 + 「系统繁忙」")
    void unknownExceptionReturns500() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    @Test
    @DisplayName("RequestBody 参数校验失败返回 400 + 拼接后的字段错误信息")
    void validationExceptionReturns400() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name 不能为空"));
    }

    @Test
    @DisplayName("业务代码直接抛的 RuntimeException 保留原始提示，不被「系统繁忙」盖掉")
    void rawRuntimeExceptionKeepsMessage() throws Exception {
        mockMvc.perform(get("/test/raw-runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("账户不存在"));
    }

    @Test
    @DisplayName("空指针等代码缺陷仍然只回「系统繁忙」，不泄露内部细节")
    void nullPointerKeepsGenericMessage() throws Exception {
        mockMvc.perform(get("/test/npe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    @Test
    @DisplayName("IllegalArgumentException 返回 400 + 原始提示")
    void illegalArgumentReturns400() throws Exception {
        mockMvc.perform(get("/test/illegal-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账户 ID 不能为空"));
    }

    @Test
    @DisplayName("参数类型不匹配返回 400")
    void typeMismatchReturns400() throws Exception {
        mockMvc.perform(get("/test/require-param").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数类型不正确: id"));
    }

    @Test
    @DisplayName("BusinessException 继承 BizException，同样被统一捕获")
    void businessExceptionIsHandled() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("库存不足"));
    }

    @Test
    @DisplayName("未指定错误码的业务异常默认按 400 处理（不是 500）")
    void businessExceptionWithoutCodeDefaultsTo400() throws Exception {
        mockMvc.perform(get("/test/business-default"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仓库不存在"));
    }

    @Test
    @DisplayName("缺少必填请求参数返回 400，而不是掉进兜底分支报 500")
    void missingRequestParamReturns400() throws Exception {
        mockMvc.perform(get("/test/require-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必填参数: id"));
    }

    @Test
    @DisplayName("请求体 JSON 格式错误返回 400")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体格式错误，无法解析为合法 JSON"));
    }

    @Test
    @DisplayName("请求方法不支持返回 405")
    void methodNotSupportedReturns405() throws Exception {
        mockMvc.perform(post("/test/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }

    @Test
    @DisplayName("平台接口异常返回 502，并透出平台真实错误信息")
    void platformApiExceptionReturns502() throws Exception {
        mockMvc.perform(get("/test/platform"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value("平台接口调用失败: [Amazon] InvalidSignature: 签名校验失败"));
    }

    static class NameRequest {

        @NotBlank(message = "name 不能为空")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/biz")
        public Result<?> biz() {
            throw new BizException(1001, "业务规则不满足");
        }

        @GetMapping("/biz-404")
        public Result<?> biz404() {
            throw new BizException(404, "资源不存在");
        }

        @GetMapping("/boom")
        public Result<?> boom() {
            // 无 message 的系统异常：不应该把空消息返回给前端
            throw new IllegalStateException();
        }

        @PostMapping("/validate")
        public Result<?> validate(@Valid @RequestBody NameRequest req) {
            return Result.success(req.getName());
        }

        @GetMapping("/business")
        public Result<?> business() {
            throw new BusinessException(1002, "库存不足");
        }

        @GetMapping("/business-default")
        public Result<?> businessDefault() {
            throw new BusinessException("仓库不存在");
        }

        @GetMapping("/require-param")
        public Result<?> requireParam(@RequestParam("id") Long id) {
            return Result.success(id);
        }

        @GetMapping("/platform")
        public Result<?> platform() {
            throw PlatformApiException.amazon("InvalidSignature", "签名校验失败");
        }

        @GetMapping("/raw-runtime")
        public Result<?> rawRuntime() {
            throw new RuntimeException("账户不存在");
        }

        @GetMapping("/npe")
        public Result<?> npe() {
            throw new NullPointerException("Cannot invoke \"FinanceAccount.getId()\" because \"account\" is null");
        }

        @GetMapping("/illegal-arg")
        public Result<?> illegalArg() {
            throw new IllegalArgumentException("账户 ID 不能为空");
        }
    }
}
