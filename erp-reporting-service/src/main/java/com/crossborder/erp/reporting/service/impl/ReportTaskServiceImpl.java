package com.crossborder.erp.reporting.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.reporting.dto.ReportGenerateRequest;
import com.crossborder.erp.reporting.dto.ReportListItemDTO;
import com.crossborder.erp.reporting.entity.BaseReport;
import com.crossborder.erp.reporting.entity.FinanceReport;
import com.crossborder.erp.reporting.entity.InventoryReport;
import com.crossborder.erp.reporting.entity.SalesReport;
import com.crossborder.erp.reporting.service.FinanceReportService;
import com.crossborder.erp.reporting.service.InventoryReportService;
import com.crossborder.erp.reporting.service.ReportTaskService;
import com.crossborder.erp.reporting.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTaskServiceImpl implements ReportTaskService {

    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int LIST_LIMIT = 50;

    private final SalesReportService salesReportService;
    private final InventoryReportService inventoryReportService;
    private final FinanceReportService financeReportService;

    @Override
    public List<ReportListItemDTO> list() {
        List<ReportListItemDTO> items = new ArrayList<>();

        salesReportService.list(new LambdaQueryWrapper<SalesReport>()
                        .orderByDesc(SalesReport::getCreateTime)
                        .last("limit " + LIST_LIMIT))
                .forEach(r -> items.add(toItem(r, "sales", "销售报表")));

        inventoryReportService.list(new LambdaQueryWrapper<InventoryReport>()
                        .orderByDesc(InventoryReport::getCreateTime)
                        .last("limit " + LIST_LIMIT))
                .forEach(r -> items.add(toItem(r, "inventory", "库存报表")));

        financeReportService.list(new LambdaQueryWrapper<FinanceReport>()
                        .orderByDesc(FinanceReport::getCreateTime)
                        .last("limit " + LIST_LIMIT))
                .forEach(r -> items.add(toItem(r, "finance", "财务报表")));

        items.sort(Comparator.comparing(
                ReportListItemDTO::getGeneratedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return items;
    }

    @Override
    public ReportListItemDTO generate(ReportGenerateRequest request) {
        if (request == null || !StringUtils.hasText(request.getType())) {
            throw new IllegalArgumentException("报表类型不能为空");
        }
        String type = request.getType().toLowerCase();
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now();

        BaseReport report = switch (type) {
            case "sales" -> salesReportService.generateDailyReport(
                    request.getPlatform(), request.getStoreId(), date);
            case "inventory" -> inventoryReportService.generateDailyReport(
                    request.getStoreId(), date);
            case "finance" -> financeReportService.generateDailyReport(
                    request.getStoreId(), date);
            default -> throw new IllegalArgumentException(
                    "不支持的报表类型: " + request.getType() + "（支持 sales / inventory / finance）");
        };

        log.info("报表生成完成: type={}, date={}, id={}", type, date, report.getId());
        return toItem(report, type, displayName(type));
    }

    @Override
    public List<Map<String, Object>> templates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        templates.add(template("sales", "销售报表", "按日/周/月汇总订单量、销售额、退款与新老客占比", "DAILY"));
        templates.add(template("inventory", "库存报表", "按仓库统计 SKU 数、库存量、呆滞金额与出入库", "DAILY"));
        templates.add(template("finance", "财务报表", "收入、成本、毛利、费用、应收应付与现金流", "MONTHLY"));
        return templates;
    }

    private Map<String, Object> template(String type, String name, String description, String period) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("name", name);
        map.put("description", description);
        map.put("defaultPeriod", period);
        return map;
    }

    private String displayName(String type) {
        return switch (type) {
            case "inventory" -> "库存报表";
            case "finance" -> "财务报表";
            default -> "销售报表";
        };
    }

    private ReportListItemDTO toItem(BaseReport r, String type, String namePrefix) {
        String period = r.getReportDate() != null ? PERIOD.format(r.getReportDate()) : null;
        return ReportListItemDTO.builder()
                .id(r.getId())
                .name(period != null ? namePrefix + " " + period : namePrefix)
                .type(type)
                .period(period)
                .granularity(r.getReportType())
                .generatedAt(r.getCreateTime())
                .status(r.getStatus())
                .build();
    }
}
