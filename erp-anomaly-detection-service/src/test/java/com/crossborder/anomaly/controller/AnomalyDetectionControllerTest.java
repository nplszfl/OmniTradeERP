package com.crossborder.anomaly.controller;

import com.crossborder.anomaly.dto.OrderFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 异常检测接口契约测试
 *
 * <p>⚠️ 重点锁死<b>响应形态</b>，因为前端是这么用的：</p>
 * <pre>
 *   list.value = await anomalyApi.list(filter)   // 然后 :data="list"
 *   stats.value = await anomalyApi.stats()       // 然后 stats.total
 * </pre>
 * axios 拦截器返回的是 {@code response.data}（响应体本身），
 * 所以后端<b>不能</b>包 Result —— 包了前端拿到的就是 {@code {code,data,message}}，
 * el-table 渲染不出行、stats.total 永远是 undefined。
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("异常检测接口契约测试")
class AnomalyDetectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void seed() {
        jdbcTemplate.update("DELETE FROM t_anomaly_record");
        jdbcTemplate.update(
                "INSERT INTO t_anomaly_record (id, order_id, order_no, platform, order_amount, risk_score, "
                        + "risk_level, anomaly_flag, anomaly_types, status, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                1L, "ORD-1001", "IO-202607-0004", "amazon", new BigDecimal("89.00"),
                92, "CRITICAL", true, "[\"金额异常\"]", "PENDING",
                java.sql.Timestamp.valueOf("2026-07-08 10:32:00"));
        jdbcTemplate.update(
                "INSERT INTO t_anomaly_record (id, order_id, order_no, platform, order_amount, risk_score, "
                        + "risk_level, anomaly_flag, anomaly_types, status, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                2L, "ORD-1002", "IO-202606-0002", "tiktok", new BigDecimal("120.00"),
                12, "LOW", false, "[]", "REJECTED",
                java.sql.Timestamp.valueOf("2026-06-25 16:00:00"));
    }

    @Test
    @DisplayName("GET /orders 必须返回裸数组（前端 :data=\"list\" 直接用）")
    void ordersReturnsBareArray() throws Exception {
        seed();
        mockMvc.perform(get("/api/v1/anomaly/orders"))
                .andExpect(status().isOk())
                // 顶层必须是数组 —— 包了 Result 就是对象，前端渲染不出来
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                // 前端 Anomaly.vue 直接读的字段
                .andExpect(jsonPath("$[0].orderNo").value("IO-202607-0004"))
                .andExpect(jsonPath("$[0].platform").value("amazon"))
                .andExpect(jsonPath("$[0].amount").value(89.00))
                .andExpect(jsonPath("$[0].anomalyScore").value(92))
                .andExpect(jsonPath("$[0].anomalyType").value("金额异常"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].detectedAt").exists());
    }

    @Test
    @DisplayName("GET /orders 支持按状态过滤")
    void ordersFiltersByStatus() throws Exception {
        seed();
        mockMvc.perform(get("/api/v1/anomaly/orders").param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("REJECTED"));
    }

    @Test
    @DisplayName("GET /stats 必须返回裸对象且带 total/normal/anomaly/rate")
    void statsReturnsBareObject() throws Exception {
        seed();
        mockMvc.perform(get("/api/v1/anomaly/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.normal").value(1))
                .andExpect(jsonPath("$.anomaly").value(1))
                .andExpect(jsonPath("$.rate").exists())
                // 不能是 Result 包装
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("POST /{id}/confirm 修改复核状态")
    void confirmUpdatesStatus() throws Exception {
        seed();
        mockMvc.perform(post("/api/v1/anomaly/1/confirm")
                        .param("status", "CONFIRMED")
                        .param("remark", "确认为异常")
                        .param("operator", "ops01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/anomaly/orders").param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("POST /detect/{orderId} 按订单重新检测")
    void detectByOrderIdWorks() throws Exception {
        mockMvc.perform(post("/api/v1/anomaly/detect/ORD-777")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"amazon\",\"orderAmount\":9999,\"itemCount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-777"))
                .andExpect(jsonPath("$.riskScore").exists());
    }

    @Test
    @DisplayName("GET /{id} 返回单条记录")
    void getOneReturnsRecord() throws Exception {
        seed();
        mockMvc.perform(get("/api/v1/anomaly/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value("IO-202607-0004"));
    }
}
