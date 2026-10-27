package com.crossborder.erp.finance;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 财务 Controller 接口契约测试
 *
 * <p>校验前端 {@code erp-web/src/api/finance.ts} 实际消费的响应形态：
 * <ul>
 *   <li>{@code /finance/account/list} 必须返回<b>裸数组</b>（前端 {@code res || []}）</li>
 *   <li>{@code /finance/category/list} 必须返回裸数组</li>
 *   <li>{@code /finance/flow/list} 必须返回 {@code Result} 包装（前端 {@code res.data.records}）</li>
 *   <li>{@code /finance/flow/statistics} 必须返回裸对象（前端 {@code res.totalIncome}）</li>
 *   <li>字段名必须是 {@code accountName} / {@code isDefault} / 小写 {@code type}</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FinanceControllerTest {

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
        jdbcTemplate.update("ALTER TABLE fin_account ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.update("ALTER TABLE fin_record ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.update("ALTER TABLE fin_category ALTER COLUMN id RESTART WITH 1");
    }

    private JsonNode call(String method, String url, Object body, Map<String, String> params)
            throws Exception {
        Map<String, String> allParams = params == null ? new HashMap<>() : params;
        if ("GET".equals(method)) {
            MvcResult result = mockMvc.perform(get(url)
                    .param("current", allParams.getOrDefault("current", "1"))
                    .param("size", allParams.getOrDefault("size", "10"))
                    .param("accountId", allParams.getOrDefault("accountId", ""))
                    .param("type", allParams.getOrDefault("type", ""))
                    .param("categoryId", allParams.getOrDefault("categoryId", ""))
                    .param("startDate", allParams.getOrDefault("startDate", ""))
                    .param("endDate", allParams.getOrDefault("endDate", ""))
                    .param("keyword", allParams.getOrDefault("keyword", ""))
                    .param("days", allParams.getOrDefault("days", "30"))).andReturn();
            return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
        if ("POST".equals(method)) {
            MvcResult result = mockMvc.perform(post(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))).andReturn();
            return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
        if ("PUT".equals(method)) {
            MvcResult result = mockMvc.perform(put(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))).andReturn();
            return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
        MvcResult result = mockMvc.perform(delete(url)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Map<String, Object> accountPayload(String name, String accountNo, String balance) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountName", name);
        payload.put("accountNo", accountNo);
        payload.put("accountType", "bank");
        payload.put("bankName", "测试银行");
        payload.put("currency", "CNY");
        payload.put("balance", new java.math.BigDecimal(balance));
        payload.put("isDefault", 0);
        payload.put("status", 1);
        return payload;
    }

    // ==================== 账户 ====================

    @Test
    @DisplayName("账户列表：返回裸数组且字段为 accountName / isDefault")
    public void testListAccountsShape() throws Exception {
        call("POST", "/finance/account", accountPayload("招行账户", "6225880000001", "880.00"), null);

        JsonNode json = call("GET", "/finance/account/list", null, null);

        assertTrue(json.isArray(), "前端用 res || [] 消费，账户列表必须返回裸数组，实际: " + json.toString());
        assertEquals(1, json.size());
        JsonNode account = json.get(0);
        assertEquals("招行账户", account.get("accountName").asText());
        assertEquals("6225880000001", account.get("accountNo").asText());
        assertEquals("bank", account.get("accountType").asText());
        assertEquals(1, account.get("status").asInt());
        assertEquals(0, account.get("isDefault").asInt());
        assertEquals(880.0, account.get("balance").asDouble(), 0.001);
    }

    @Test
    @DisplayName("创建账户：初始余额原样入账，不被清零")
    public void testCreateAccountKeepsInitialBalance() throws Exception {
        JsonNode json = call("POST", "/finance/account", accountPayload("余额账户", "6225880000002", "1234.56"), null);

        assertEquals(200, json.get("code").asInt());
        assertEquals(1234.56, json.get("data").get("balance").asDouble(), 0.001);
    }

    @Test
    @DisplayName("设置默认账户：唯一且可回读")
    public void testSetDefaultAccount() throws Exception {
        call("POST", "/finance/account", accountPayload("默认账户", "6225880000003", "0"), null);
        call("POST", "/finance/account", accountPayload("普通账户", "6225880000004", "0"), null);

        JsonNode list = call("GET", "/finance/account/list", null, null);
        long defaultId = -1;
        long otherId = -1;
        for (JsonNode account : list) {
            if ("默认账户".equals(account.get("accountName").asText())) {
                defaultId = account.get("id").asLong();
            } else {
                otherId = account.get("id").asLong();
            }
        }

        call("POST", "/finance/account/" + defaultId + "/default", null, null);

        JsonNode after = call("GET", "/finance/account/list", null, null);
        for (JsonNode account : after) {
            int expected = account.get("id").asLong() == defaultId ? 1 : 0;
            assertEquals(expected, account.get("isDefault").asInt(),
                    "同一时刻只能有一个默认账户");
        }
        assertTrue(otherId > 0);
    }

    @Test
    @DisplayName("更新账户：余额不被直接改写")
    public void testUpdateAccountRejectsBalanceChange() throws Exception {
        JsonNode created = call("POST", "/finance/account", accountPayload("改余额账户", "6225880000005", "100"), null);
        long id = created.get("data").get("id").asLong();

        Map<String, Object> update = accountPayload("改余额账户", "6225880000005", "99999");
        update.put("id", id);
        call("PUT", "/finance/account", update, null);

        JsonNode list = call("GET", "/finance/account/list", null, null);
        assertEquals(100.0, list.get(0).get("balance").asDouble(), 0.001);
    }

    // ==================== 流水 ====================

    @Test
    @DisplayName("流水分页：Result 包装 + data.records / data.total")
    public void testListFlowsShape() throws Exception {
        JsonNode account = call("POST", "/finance/account", accountPayload("流水账户", "6225880000006", "0"), null);
        long accountId = account.get("data").get("id").asLong();

        Map<String, Object> flow = new HashMap<>();
        flow.put("type", "income");
        flow.put("amount", new java.math.BigDecimal("500"));
        flow.put("accountId", accountId);
        flow.put("remark", "接口契约测试");
        call("POST", "/finance/flow", flow, null);

        JsonNode json = call("GET", "/finance/flow/list", null, Map.of("current", "1", "size", "10"));

        assertTrue(json.has("data"), "前端用 res.data.records 消费，流水分页必须返回 Result 包装");
        assertEquals(1, json.get("data").get("total").asInt());
        JsonNode record = json.get("data").get("records").get(0);
        assertEquals("income", record.get("type").asText(), "流水类型必须是小写 income/expense/refund");
        assertEquals("接口契约测试", record.get("remark").asText());
        assertEquals("流水账户", record.get("accountName").asText());
    }

    @Test
    @DisplayName("收支概览：返回裸对象 totalIncome / totalExpense / profit / recordCount")
    public void testFlowStatisticsShape() throws Exception {
        JsonNode account = call("POST", "/finance/account", accountPayload("统计账户", "6225880000007", "0"), null);
        long accountId = account.get("data").get("id").asLong();

        Map<String, Object> income = new HashMap<>();
        income.put("type", "income");
        income.put("amount", new java.math.BigDecimal("1000"));
        income.put("accountId", accountId);
        call("POST", "/finance/flow", income, null);

        Map<String, Object> expense = new HashMap<>();
        expense.put("type", "expense");
        expense.put("amount", new java.math.BigDecimal("250"));
        expense.put("accountId", accountId);
        call("POST", "/finance/flow", expense, null);

        JsonNode json = call("GET", "/finance/flow/statistics", null, null);

        assertTrue(!json.has("code"), "前端用 res.totalIncome 消费，概览必须返回裸对象，实际: " + json.toString());
        assertEquals(1000.0, json.get("totalIncome").asDouble(), 0.001);
        assertEquals(250.0, json.get("totalExpense").asDouble(), 0.001);
        assertEquals(750.0, json.get("profit").asDouble(), 0.001);
        assertEquals(2, json.get("recordCount").asInt());
    }

    @Test
    @DisplayName("趋势与分类：返回裸数组")
    public void testTrendAndCategoryShape() throws Exception {
        JsonNode account = call("POST", "/finance/account", accountPayload("图表账户", "6225880000008", "0"), null);
        long accountId = account.get("data").get("id").asLong();

        Map<String, Object> income = new HashMap<>();
        income.put("type", "income");
        income.put("amount", new java.math.BigDecimal("300"));
        income.put("accountId", accountId);
        call("POST", "/finance/flow", income, null);

        JsonNode trend = call("GET", "/finance/flow/trend", null, Map.of("days", "7"));
        assertTrue(trend.isArray(), "前端用 trendData || [] 消费，趋势必须返回裸数组");
        assertTrue(trend.get(0).has("date"));
        assertTrue(trend.get(0).has("income"));
        assertTrue(trend.get(0).has("expense"));

        JsonNode category = call("GET", "/finance/flow/category", null, null);
        assertTrue(category.isArray(), "前端用 categoryData || [] 消费，分类构成必须返回裸数组");
    }

    @Test
    @DisplayName("删除流水：回退账户余额并从列表消失")
    public void testDeleteFlowRevertsBalance() throws Exception {
        JsonNode account = call("POST", "/finance/account", accountPayload("删除账户", "6225880000009", "100"), null);
        long accountId = account.get("data").get("id").asLong();

        Map<String, Object> income = new HashMap<>();
        income.put("type", "income");
        income.put("amount", new java.math.BigDecimal("50"));
        income.put("accountId", accountId);
        JsonNode created = call("POST", "/finance/flow", income, null);
        long flowId = created.get("data").get("id").asLong();

        call("DELETE", "/finance/flow/" + flowId, null, null);

        JsonNode accounts = call("GET", "/finance/account/list", null, null);
        assertEquals(100.0, accounts.get(0).get("balance").asDouble(), 0.001,
                "删除流水必须把账户余额改回去");

        JsonNode flows = call("GET", "/finance/flow/list", null, Map.of("current", "1", "size", "10"));
        assertEquals(0, flows.get("data").get("total").asInt(), "已删除的流水不应再出现在列表中");
    }

    // ==================== 分类 ====================

    @Test
    @DisplayName("分类列表：返回裸数组且字段为 name / sort / status")
    public void testListCategoriesShape() throws Exception {
        Map<String, Object> category = new HashMap<>();
        category.put("name", "销售收入");
        category.put("type", "income");
        category.put("sort", 3);
        category.put("status", 1);
        call("POST", "/finance/category", category, null);

        JsonNode json = call("GET", "/finance/category/list", null, null);

        assertTrue(json.isArray(), "前端用 res || [] 消费，分类列表必须返回裸数组");
        assertEquals(1, json.size());
        assertEquals("销售收入", json.get(0).get("name").asText());
        assertEquals("income", json.get(0).get("type").asText());
        assertEquals(3, json.get(0).get("sort").asInt());
        assertEquals(1, json.get(0).get("status").asInt());
    }

    @Test
    @DisplayName("按日期过滤：startDate / endDate 生效")
    public void testListFlowsByDateRange() throws Exception {
        JsonNode account = call("POST", "/finance/account", accountPayload("日期账户", "6225880000010", "0"), null);
        long accountId = account.get("data").get("id").asLong();

        Map<String, Object> income = new HashMap<>();
        income.put("type", "income");
        income.put("amount", new java.math.BigDecimal("10"));
        income.put("accountId", accountId);
        call("POST", "/finance/flow", income, null);

        Map<String, String> today = Map.of(
                "current", "1", "size", "10",
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().toString());
        assertEquals(1, call("GET", "/finance/flow/list", null, today).get("data").get("total").asInt());

        Map<String, String> yesterday = Map.of(
                "current", "1", "size", "10",
                "startDate", LocalDate.now().minusDays(2).toString(),
                "endDate", LocalDate.now().minusDays(1).toString());
        assertEquals(0, call("GET", "/finance/flow/list", null, yesterday).get("data").get("total").asInt());
    }
}
