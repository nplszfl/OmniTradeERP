package com.crossborder.erp.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 财务模块错误响应契约测试（端到端）
 *
 * <p><b>为什么需要这个测试</b>：{@code GlobalExceptionHandler} 位于 erp-common，
 * 而各服务的组件扫描范围不包含 {@code com.crossborder.erp.common}，
 * 它靠 {@code CommonWebAutoConfiguration} 自动装配才生效。
 * 如果哪天自动装配没被加载，异常就会退回 Spring 默认错误体
 * （{@code {timestamp,status,error,path}}），
 * 前端 {@code error.response?.data?.message} 取不到值，只会弹一句
 * "Request failed with status code 500" —— 这种问题单测根本发现不了，
 * 必须跑真实 Spring 上下文 + 真实 HTTP 请求才能锁死。</p>
 *
 * <p>锁死三件事：</p>
 * <ul>
 *   <li>业务异常 → HTTP 400（不是 500，避免监控里出现假的「服务端故障」）</li>
 *   <li>响应体是统一的 {@code Result{code,message,data}}，不是 Spring 默认错误体</li>
 *   <li>异常里带的真实提示原样透出（"账户名已存在"），不被 "系统繁忙" 盖掉</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FinanceErrorContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM fin_record");
        jdbcTemplate.update("DELETE FROM fin_account");
        jdbcTemplate.update("DELETE FROM fin_category");
        jdbcTemplate.update("DELETE FROM fin_statistics");
    }

    private MvcResult postAccount(String name, String accountNo) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountName", name);
        payload.put("accountNo", accountNo);
        payload.put("accountType", "bank");
        payload.put("bankName", "测试银行");
        payload.put("currency", "CNY");
        payload.put("balance", new java.math.BigDecimal("0"));
        payload.put("isDefault", 0);
        payload.put("status", 1);
        return mockMvc.perform(post("/finance/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))).andReturn();
    }

    private MvcResult deleteAccount(long id) throws Exception {
        return mockMvc.perform(delete("/finance/account/" + id)).andReturn();
    }

    @Test
    @DisplayName("业务异常：HTTP 400 + Result 响应体 + 真实提示")
    void businessErrorReturns400WithResultEnvelope() throws Exception {
        MvcResult created = postAccount("重名账户", "6225880009001");
        assertEquals(200, created.getResponse().getStatus());

        MvcResult duplicated = postAccount("重名账户", "6225880009002");

        assertEquals(400, duplicated.getResponse().getStatus(),
                "账户名重复是客户端错误，必须是 400 而不是 500");

        String body = duplicated.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\""), "响应体必须是统一 Result 结构，实际: " + body);
        assertTrue(body.contains("\"message\""), "响应体必须含 message 字段，实际: " + body);
        assertTrue(body.contains("账户名已存在"),
                "真实业务提示必须透出，不能被 '系统繁忙' 盖掉，实际: " + body);
        assertTrue(!body.contains("系统繁忙"), "业务异常不该返回兜底文案，实际: " + body);
    }

    @Test
    @DisplayName("删除不存在的账户：HTTP 400 + 统一响应体")
    void deletingMissingAccountReturns400() throws Exception {
        MvcResult result = deleteAccount(999999L);

        assertEquals(400, result.getResponse().getStatus());

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\""), "响应体必须是统一 Result 结构，实际: " + body);
        assertTrue(body.contains("账户不存在"), "真实业务提示必须透出，实际: " + body);
    }
}
