package com.crossborder.erp.reporting.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.reporting.dto.ReportQueryDTO;
import com.crossborder.erp.reporting.entity.SalesReport;
import com.crossborder.erp.reporting.mapper.SalesReportMapper;
import com.crossborder.erp.reporting.service.impl.SalesReportServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.ReflectionUtils;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 销售报表服务单元测试
 *
 * 覆盖 SalesReportServiceImpl 的日/周/月报表生成、多条件分页查询与 Excel 导出。
 * 纯单元测试（mock mapper），不依赖数据库。
 */
@SuppressWarnings("unchecked")
public class SalesReportServiceImplTest {

    private SalesReportServiceImpl salesReportService;
    private SalesReportMapper salesReportMapper;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析字段名依赖 MyBatis-Plus 的 TableInfo / Lambda 缓存
        MapperBuilderAssistant builderAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        builderAssistant.setCurrentNamespace(
                "com.crossborder.erp.reporting.mapper.SalesReportMapper");
        TableInfoHelper.initTableInfo(builderAssistant, SalesReport.class);
    }

    @BeforeEach
    void setUp() {
        salesReportMapper = mock(SalesReportMapper.class);
        salesReportService = new SalesReportServiceImpl();
        injectBaseMapper(salesReportService, salesReportMapper);

        when(salesReportMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<SalesReport> page = invocation.getArgument(0);
            return page;
        });
    }

    /**
     * 通过反射把 mock mapper 注入 ServiceImpl 的 baseMapper 字段
     */
    private void injectBaseMapper(ServiceImpl<?, ?> service, SalesReportMapper mapper) {
        try {
            Field field = ReflectionUtils.findField(ServiceImpl.class, "baseMapper");
            if (field == null) {
                throw new IllegalStateException("未找到 ServiceImpl.baseMapper 字段");
            }
            ReflectionUtils.makeAccessible(field);
            field.set(service, mapper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("注入 baseMapper 失败", e);
        }
    }

    private SalesReport buildReport(String type, LocalDate date, String platform, String storeId) {
        SalesReport report = new SalesReport();
        report.setId(1L);
        report.setReportType(type);
        report.setReportDate(date);
        report.setPlatform(platform);
        report.setStoreId(storeId);
        report.setOrderCount(10);
        report.setOrderQuantity(25);
        report.setTotalAmount(new BigDecimal("1000.00"));
        report.setRefundAmount(new BigDecimal("100.00"));
        report.setNetAmount(new BigDecimal("900.00"));
        report.setCreateTime(LocalDateTime.of(2026, 10, 28, 10, 0));
        return report;
    }

    // ==================== 报表生成 ====================

    @Test
    @DisplayName("测试生成日销售报表 - 类型/日期/状态与初始值")
    void testGenerateDailyReport_SetsDefaults() {
        when(salesReportMapper.insert(any(SalesReport.class))).thenReturn(1);
        LocalDate today = LocalDate.of(2026, 10, 28);

        SalesReport report = salesReportService.generateDailyReport("AMAZON", "STORE001", today);

        assertNotNull(report);
        assertEquals("DAILY", report.getReportType());
        assertEquals(today, report.getReportDate());
        assertEquals("AMAZON", report.getPlatform());
        assertEquals("STORE001", report.getStoreId());
        assertEquals("GENERATED", report.getStatus());
        assertEquals(0, report.getOrderCount());
        assertEquals(0, report.getOrderQuantity());
        assertEquals(BigDecimal.ZERO, report.getTotalAmount());
        assertEquals(BigDecimal.ZERO, report.getRefundAmount());
        assertEquals(BigDecimal.ZERO, report.getNetAmount());

        verify(salesReportMapper, times(1)).insert(any(SalesReport.class));
    }

    @Test
    @DisplayName("测试生成周销售报表 - 类型为 WEEKLY")
    void testGenerateWeeklyReport_WeeklyType() {
        LocalDate weekStart = LocalDate.of(2026, 10, 26);

        SalesReport report = salesReportService.generateWeeklyReport("EBAY", "STORE002", weekStart);

        assertEquals("WEEKLY", report.getReportType());
        assertEquals(weekStart, report.getReportDate());
        assertEquals("EBAY", report.getPlatform());
        assertEquals("GENERATED", report.getStatus());
    }

    @Test
    @DisplayName("测试生成月销售报表 - 报表日期为当月1号")
    void testGenerateMonthlyReport_FirstDayOfMonth() {
        SalesReport report = salesReportService.generateMonthlyReport("SHEIN", "STORE003", 2026, 10);

        assertEquals("MONTHLY", report.getReportType());
        assertEquals(LocalDate.of(2026, 10, 1), report.getReportDate());
        assertEquals("SHEIN", report.getPlatform());
        assertEquals("GENERATED", report.getStatus());
    }

    // ==================== queryReports ====================

    @Test
    @DisplayName("测试查询销售报表 - 返回分页结果")
    void testQueryReports_ReturnsPage() {
        when(salesReportMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<SalesReport> page = invocation.getArgument(0);
            page.setRecords(List.of(buildReport("DAILY", LocalDate.of(2026, 10, 28), "AMAZON", "STORE001")));
            page.setTotal(1);
            return page;
        });

        ReportQueryDTO query = new ReportQueryDTO();
        IPage<SalesReport> result = salesReportService.queryReports(query, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("测试查询销售报表 - 应用全部过滤条件")
    void testQueryReports_AppliesAllFilters() {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType("DAILY");
        query.setStartDate(LocalDate.of(2026, 10, 1));
        query.setEndDate(LocalDate.of(2026, 10, 31));
        query.setPlatform("AMAZON");
        query.setStoreId("STORE001");

        salesReportService.queryReports(query, 1, 10);

        ArgumentCaptor<Wrapper<SalesReport>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(salesReportMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("report_type"), "应包含报表类型过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("report_date"), "应包含报表日期范围: " + sqlSegment);
        assertTrue(sqlSegment.contains("platform"), "应包含平台过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("store_id"), "应包含店铺过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("create_time"), "应按创建时间排序: " + sqlSegment);
    }

    @Test
    @DisplayName("测试查询销售报表 - 空查询条件不产生额外过滤")
    void testQueryReports_EmptyQuery_NoFilters() {
        ReportQueryDTO query = new ReportQueryDTO();

        salesReportService.queryReports(query, 1, 10);

        ArgumentCaptor<Wrapper<SalesReport>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(salesReportMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertFalse(sqlSegment.contains("report_type"), "空条件不应包含报表类型: " + sqlSegment);
        assertFalse(sqlSegment.contains("platform"), "空条件不应包含平台: " + sqlSegment);
        assertFalse(sqlSegment.contains("store_id"), "空条件不应包含店铺: " + sqlSegment);
    }

    // ==================== exportExcel ====================

    @Test
    @DisplayName("测试导出Excel - 生成含表头与数据行的有效工作簿")
    void testExportExcel_ProducesValidWorkbook() throws Exception {
        when(salesReportMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                buildReport("DAILY", LocalDate.of(2026, 10, 28), "AMAZON", "STORE001")
        ));

        byte[] bytes = salesReportService.exportExcel(new ReportQueryDTO());

        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "导出内容不应为空");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("销售报表");
            assertNotNull(sheet, "应包含名为「销售报表」的工作表");

            Row header = sheet.getRow(0);
            assertNotNull(header, "应包含表头行");
            assertEquals(10, header.getLastCellNum(), "表头应有 10 列");
            assertEquals("报表类型", header.getCell(0).getStringCellValue());
            assertEquals("净金额", header.getCell(8).getStringCellValue());

            Row data = sheet.getRow(1);
            assertNotNull(data, "应包含数据行");
            assertEquals("DAILY", data.getCell(0).getStringCellValue());
            assertEquals("2026-10-28", data.getCell(1).getStringCellValue());
            assertEquals("AMAZON", data.getCell(2).getStringCellValue());
            assertEquals("STORE001", data.getCell(3).getStringCellValue());
            assertEquals(10, (int) data.getCell(4).getNumericCellValue());
            assertEquals(1000.0, data.getCell(6).getNumericCellValue(), 0.001);
            assertEquals(900.0, data.getCell(8).getNumericCellValue(), 0.001);
        }
    }

    @Test
    @DisplayName("测试导出Excel - 空结果时只输出表头")
    void testExportExcel_EmptyResult_HeaderOnly() throws Exception {
        when(salesReportMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        byte[] bytes = salesReportService.exportExcel(new ReportQueryDTO());

        assertNotNull(bytes);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("销售报表");
            assertNotNull(sheet);
            assertNotNull(sheet.getRow(0), "应包含表头行");
            assertNull(sheet.getRow(1), "无数据时不应有数据行");
        }
    }

    @Test
    @DisplayName("测试导出Excel - 金额为 null 时写 0 而不抛异常")
    void testExportExcel_NullAmounts_WrittenAsZero() throws Exception {
        SalesReport report = new SalesReport();
        report.setReportType("DAILY");
        report.setReportDate(LocalDate.of(2026, 10, 28));
        report.setPlatform("AMAZON");
        report.setStoreId("STORE001");
        report.setOrderCount(3);
        report.setOrderQuantity(7);
        report.setTotalAmount(null);
        report.setRefundAmount(null);
        report.setNetAmount(null);
        report.setCreateTime(null);

        when(salesReportMapper.selectList(any(Wrapper.class))).thenReturn(List.of(report));

        byte[] bytes = salesReportService.exportExcel(new ReportQueryDTO());

        assertNotNull(bytes);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("销售报表");
            Row data = sheet.getRow(1);
            assertNotNull(data);
            assertEquals(0.0, data.getCell(6).getNumericCellValue(), 0.001);
            assertEquals(0.0, data.getCell(7).getNumericCellValue(), 0.001);
            assertEquals(0.0, data.getCell(8).getNumericCellValue(), 0.001);
            assertEquals("", data.getCell(9).getStringCellValue());
        }
    }

    @Test
    @DisplayName("测试导出Excel - 应用查询过滤条件")
    void testExportExcel_AppliesFilters() {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType("WEEKLY");
        query.setPlatform("EBAY");

        salesReportService.exportExcel(query);

        ArgumentCaptor<Wrapper<SalesReport>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(salesReportMapper, times(1)).selectList(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("report_type"), "应包含报表类型过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("platform"), "应包含平台过滤: " + sqlSegment);
    }
}
