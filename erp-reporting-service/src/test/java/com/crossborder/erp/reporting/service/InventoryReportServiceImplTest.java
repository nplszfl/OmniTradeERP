package com.crossborder.erp.reporting.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.reporting.dto.ReportQueryDTO;
import com.crossborder.erp.reporting.entity.InventoryReport;
import com.crossborder.erp.reporting.mapper.InventoryReportMapper;
import com.crossborder.erp.reporting.service.impl.InventoryReportServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
 * 库存报表服务单元测试
 *
 * 重点覆盖 exportExcel —— 此前因 poi-ooxml-schemas:4.1.2 与 poi-ooxml-lite:5.2.5
 * 类冲突导致 new XSSFWorkbook() 抛 NoSuchFieldError，三个导出接口全部 500。
 * 本测试作为该缺陷的回归防线。
 */
@SuppressWarnings("unchecked")
public class InventoryReportServiceImplTest {

    private InventoryReportServiceImpl inventoryReportService;
    private InventoryReportMapper inventoryReportMapper;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant builderAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        builderAssistant.setCurrentNamespace(
                "com.crossborder.erp.reporting.mapper.InventoryReportMapper");
        TableInfoHelper.initTableInfo(builderAssistant, InventoryReport.class);
    }

    @BeforeEach
    void setUp() {
        inventoryReportMapper = mock(InventoryReportMapper.class);
        inventoryReportService = new InventoryReportServiceImpl();
        injectBaseMapper(inventoryReportService, inventoryReportMapper);

        when(inventoryReportMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<InventoryReport> page = invocation.getArgument(0);
            return page;
        });
    }

    private void injectBaseMapper(ServiceImpl<?, ?> service, InventoryReportMapper mapper) {
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

    private InventoryReport buildReport(String warehouseId) {
        InventoryReport report = new InventoryReport();
        report.setId(1L);
        report.setReportType("DAILY");
        report.setReportDate(LocalDate.of(2026, 10, 28));
        report.setWarehouseId(warehouseId);
        report.setWarehouseName("主仓库");
        report.setTotalSkus(120);
        report.setTotalQuantity(5000);
        report.setLowStockSkus(8);
        report.setOutOfStockSkus(2);
        report.setTotalValue(new BigDecimal("88888.00"));
        report.setCreateTime(LocalDateTime.of(2026, 10, 28, 9, 30));
        return report;
    }

    @Test
    @DisplayName("测试导出库存报表 - 生成有效工作簿且表头正确")
    void testExportExcel_ProducesValidWorkbook() throws Exception {
        when(inventoryReportMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(buildReport("WH001")));

        byte[] bytes = inventoryReportService.exportExcel(new ReportQueryDTO());

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("库存报表");
            assertNotNull(sheet, "应包含名为「库存报表」的工作表");

            Row header = sheet.getRow(0);
            assertNotNull(header);
            assertEquals(10, header.getLastCellNum());
            assertEquals("报表类型", header.getCell(0).getStringCellValue());
            assertEquals("总价值", header.getCell(8).getStringCellValue());

            Row data = sheet.getRow(1);
            assertNotNull(data);
            assertEquals("DAILY", data.getCell(0).getStringCellValue());
            assertEquals("2026-10-28", data.getCell(1).getStringCellValue());
            assertEquals("WH001", data.getCell(2).getStringCellValue());
            assertEquals(120, (int) data.getCell(4).getNumericCellValue());
            assertEquals(88888.0, data.getCell(8).getNumericCellValue(), 0.001);
        }
    }

    @Test
    @DisplayName("测试导出库存报表 - 金额为 null 时写 0")
    void testExportExcel_NullValues_WrittenAsZero() throws Exception {
        InventoryReport report = buildReport("WH001");
        report.setTotalValue(null);
        report.setCreateTime(null);

        when(inventoryReportMapper.selectList(any(Wrapper.class))).thenReturn(List.of(report));

        byte[] bytes = inventoryReportService.exportExcel(new ReportQueryDTO());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row data = workbook.getSheet("库存报表").getRow(1);
            assertNotNull(data);
            assertEquals(0.0, data.getCell(8).getNumericCellValue(), 0.001);
            assertEquals("", data.getCell(9).getStringCellValue());
        }
    }

    @Test
    @DisplayName("测试导出库存报表 - 应用仓库过滤条件")
    void testExportExcel_AppliesFilters() {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType("DAILY");
        query.setWarehouseId("WH001");

        inventoryReportService.exportExcel(query);

        ArgumentCaptor<Wrapper<InventoryReport>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(inventoryReportMapper, times(1)).selectList(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("report_type"), "应包含报表类型过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("warehouse_id"), "应包含仓库过滤: " + sqlSegment);
    }
}
