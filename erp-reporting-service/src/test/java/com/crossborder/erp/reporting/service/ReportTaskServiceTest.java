package com.crossborder.erp.reporting.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crossborder.erp.reporting.dto.ReportGenerateRequest;
import com.crossborder.erp.reporting.dto.ReportListItemDTO;
import com.crossborder.erp.reporting.dto.ReportQueryDTO;
import com.crossborder.erp.reporting.entity.FinanceReport;
import com.crossborder.erp.reporting.entity.InventoryReport;
import com.crossborder.erp.reporting.entity.SalesReport;
import com.crossborder.erp.reporting.service.impl.ReportTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报表台账服务集成测试（H2 + 真实 MyBatis-Plus）
 *
 * <p>对应前端 erp-web/src/views/report/index.vue 的三个调用：
 * list / generate / templates —— 这三个接口之前后端一个都没有，
 * 页面永远走 catch 分支的 mock 数据。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@SpringBootTest
class ReportTaskServiceTest {

    @Autowired
    private ReportTaskServiceImpl service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM t_report_sales");
        jdbcTemplate.update("DELETE FROM t_report_inventory");
        jdbcTemplate.update("DELETE FROM t_report_finance");
    }

    @Test
    @DisplayName("报表台账：汇总三类报表并带上周期粒度")
    void listAggregatesAllReportTypes() {
        jdbcTemplate.update(
                "INSERT INTO t_report_sales (id, tenant_id, report_type, report_date, status, create_time, update_time) "
                        + "VALUES (1, 't1', 'DAILY', DATE '2026-11-01', 'GENERATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbcTemplate.update(
                "INSERT INTO t_report_inventory (id, tenant_id, report_type, report_date, status, create_time, update_time) "
                        + "VALUES (2, 't1', 'MONTHLY', DATE '2026-10-01', 'GENERATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbcTemplate.update(
                "INSERT INTO t_report_finance (id, tenant_id, report_type, report_date, status, create_time, update_time) "
                        + "VALUES (3, 't1', 'DAILY', DATE '2026-11-02', 'GENERATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        List<ReportListItemDTO> items = service.list();

        assertEquals(3, items.size());
        assertTrue(items.stream().anyMatch(i -> "sales".equals(i.getType())));
        assertTrue(items.stream().anyMatch(i -> "inventory".equals(i.getType())));
        assertTrue(items.stream().anyMatch(i -> "finance".equals(i.getType())));

        ReportListItemDTO inventory = items.stream()
                .filter(i -> "inventory".equals(i.getType())).findFirst().orElseThrow();
        assertEquals("2026-10-01", inventory.getPeriod());
        assertEquals("MONTHLY", inventory.getGranularity());
        assertEquals("GENERATED", inventory.getStatus());
        assertNotNull(inventory.getGeneratedAt());
    }

    @Test
    @DisplayName("报表台账为空时返回空列表而不是 null")
    void listReturnsEmptyListWhenNoData() {
        assertNotNull(service.list());
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("生成销售报表：入库并可查到")
    void generateSalesReport() {
        ReportListItemDTO item = service.generate(ReportGenerateRequest.builder()
                .type("sales")
                .date(LocalDate.of(2026, 11, 2))
                .platform("amazon")
                .storeId("shop-1")
                .build());

        assertNotNull(item);
        assertEquals("sales", item.getType());
        assertEquals("2026-11-02", item.getPeriod());
        assertEquals("DAILY", item.getGranularity());

        List<ReportListItemDTO> items = service.list();
        assertEquals(1, items.size());
        assertEquals("sales", items.get(0).getType());
    }

    @Test
    @DisplayName("生成库存报表")
    void generateInventoryReport() {
        ReportListItemDTO item = service.generate(ReportGenerateRequest.builder()
                .type("inventory")
                .date(LocalDate.of(2026, 11, 2))
                .storeId("wh-1")
                .build());
        assertEquals("inventory", item.getType());
    }

    @Test
    @DisplayName("不支持的报表类型 → 明确报错")
    void generateRejectsUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> service.generate(
                ReportGenerateRequest.builder().type("customer").build()));
    }

    @Test
    @DisplayName("报表类型为空 → 明确报错")
    void generateRejectsBlankType() {
        assertThrows(IllegalArgumentException.class, () -> service.generate(
                ReportGenerateRequest.builder().type("  ").build()));
    }

    @Test
    @DisplayName("报表模板：三种类型都返回")
    void templatesCoverAllTypes() {
        List<?> templates = service.templates();
        assertEquals(3, templates.size());
    }
}
