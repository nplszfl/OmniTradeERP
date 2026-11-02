package com.crossborder.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.finance.converter.FinanceConverter;
import com.crossborder.erp.finance.dto.FinanceAccountRequest;
import com.crossborder.erp.finance.dto.FinanceCategoryRequest;
import com.crossborder.erp.finance.dto.FinanceFlowRequest;
import com.crossborder.erp.finance.dto.FinanceRecordQuery;
import com.crossborder.erp.finance.entity.FinanceAccount;
import com.crossborder.erp.finance.entity.FinanceCategory;
import com.crossborder.erp.finance.entity.FinanceRecord;
import com.crossborder.erp.finance.enums.CategoryType;
import com.crossborder.erp.finance.enums.FinanceType;
import com.crossborder.erp.finance.service.FinanceService;
import com.crossborder.erp.finance.vo.AccountStatistics;
import com.crossborder.erp.finance.vo.BatchFinanceResult;
import com.crossborder.erp.finance.vo.CategoryStatistics;
import com.crossborder.erp.finance.vo.FinanceAccountVO;
import com.crossborder.erp.finance.vo.FinanceCategoryVO;
import com.crossborder.erp.finance.vo.FinanceDailyStatistics;
import com.crossborder.erp.finance.vo.FinanceFlowVO;
import com.crossborder.erp.finance.vo.FinanceMonthlyStatistics;
import com.crossborder.erp.finance.vo.FinanceOverview;
import com.crossborder.erp.finance.vo.FinanceTrend;
import com.crossborder.erp.finance.vo.ReconciliationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 财务服务 Controller
 *
 * <p>路径与前端契约 {@code erp-web/src/api/finance.ts} 严格对齐：
 * <pre>
 * GET    /finance/account/list              账户列表（前端直接消费数组）
 * POST   /finance/account                  创建账户
 * PUT    /finance/account                  更新账户
 * DELETE /finance/account/{id}             删除账户
 * POST   /finance/account/{id}/default     设为默认账户
 *
 * GET    /finance/flow/list                分页查询流水（前端消费 data.records / data.total）
 * GET    /finance/flow/statistics          收支概览（前端直接消费对象）
 * GET    /finance/flow/trend               收支趋势（前端直接消费数组）
 * GET    /finance/flow/category            分类构成（前端直接消费数组）
 * GET    /finance/flow/{id}                流水详情
 * POST   /finance/flow                     创建流水
 * PUT    /finance/flow                     更新流水
 * DELETE /finance/flow/{id}                删除流水（回退余额 + 逻辑删除）
 * POST   /finance/flow/batch               批量记账
 *
 * GET    /finance/overview                 财务总览
 * GET    /finance/statistics/account       账户维度统计
 * GET    /finance/statistics/category      分类维度统计
 * GET    /finance/statistics/daily         日统计
 * GET    /finance/statistics/monthly       月统计
 * GET    /finance/statistics/trend         趋势分析
 * GET    /finance/reconcile                账户对账
 *
 * GET    /finance/category/list            分类列表（前端直接消费数组）
 * POST   /finance/category                 创建分类
 * PUT    /finance/category                 更新分类
 * DELETE /finance/category/{id}            删除分类
 * </pre>
 *
 * <p>响应包装方式按前端消费方式决定：前端用 {@code res || []} 消费的直接返回裸数组 /
 * 裸对象；前端用 {@code res.data.records} 消费的返回 {@code Result} 包装。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Slf4j
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    // ==================== 账户管理 ====================

    /**
     * 账户列表（前端：financeAccountApi.getAccounts → {@code res || []}）
     */
    @GetMapping("/account/list")
    public List<FinanceAccountVO> listAccounts() {
        return financeService.listAccounts().stream()
                .map(FinanceConverter::toAccountVO)
                .collect(Collectors.toList());
    }

    /**
     * 创建账户（前端：financeAccountApi.createAccount）
     */
    @PostMapping("/account")
    public Result<FinanceAccountVO> createAccount(@RequestBody FinanceAccountRequest request) {
        FinanceAccount account = FinanceConverter.toAccountEntity(request);
        Long id = financeService.createAccount(account);
        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            financeService.setDefaultAccount(id);
        }
        FinanceAccount created = financeService.getAccountById(id);
        return Result.success(FinanceConverter.toAccountVO(created));
    }

    /**
     * 更新账户（前端：financeAccountApi.updateAccount）
     *
     * <p>账户余额不允许在此处改写，余额只能通过记账接口变更。</p>
     */
    @PutMapping("/account")
    public Result<FinanceAccountVO> updateAccount(@RequestBody FinanceAccountRequest request) {
        if (request.getId() == null) {
            throw new BizException("账户 ID 不能为空");
        }
        FinanceAccount existing = financeService.getAccountById(request.getId());
        if (existing == null) {
            throw new BizException("账户不存在");
        }
        existing.setName(request.getAccountName());
        existing.setAccountNumber(request.getAccountNo());
        existing.setAccountType(FinanceConverter.toAccountType(request.getAccountType()));
        existing.setBankName(request.getBankName());
        existing.setCurrency(request.getCurrency());
        existing.setStatus(FinanceConverter.fromStatusCode(request.getStatus()));
        existing.setRemark(request.getRemark());
        // 余额由后端保留原值：前端表单里带着 balance，提交回来的是同一个值
        existing.setBalance(existing.getBalance());
        financeService.updateAccount(existing);

        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            financeService.setDefaultAccount(request.getId());
        }
        FinanceAccount updated = financeService.getAccountById(request.getId());
        return Result.success(FinanceConverter.toAccountVO(updated));
    }

    /**
     * 删除账户（前端：financeAccountApi.deleteAccount）
     */
    @DeleteMapping("/account/{id}")
    public Result<Void> deleteAccount(@PathVariable Long id) {
        financeService.deleteAccount(id);
        return Result.success();
    }

    /**
     * 设为默认账户（前端：financeAccountApi.setDefault）
     */
    @PostMapping("/account/{id}/default")
    public Result<Void> setDefaultAccount(@PathVariable Long id) {
        financeService.setDefaultAccount(id);
        return Result.success();
    }

    // ==================== 流水管理 ====================

    /**
     * 分页查询流水（前端：financeFlowApi.getFlows → {@code res.data.records}）
     */
    @GetMapping("/flow/list")
    public Result<Page<FinanceFlowVO>> listFlows(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword) {

        FinanceRecordQuery query = new FinanceRecordQuery();
        query.setPage(current);
        query.setSize(size);
        query.setAccountId(accountId);
        query.setCategoryId(categoryId);
        query.setKeyword(keyword);
        query.setType(parseFinanceType(type));
        query.setStartTime(parseStartTime(startDate));
        query.setEndTime(parseEndTime(endDate));

        Page<FinanceRecord> records = financeService.listRecords(query);

        Map<Long, String> accountNames = accountNameMap();
        Map<Long, String> categoryNames = categoryNameMap();

        Page<FinanceFlowVO> page = new Page<>(records.getCurrent(), records.getSize(), records.getTotal());
        page.setRecords(records.getRecords().stream()
                .map(record -> FinanceConverter.toFlowVO(record,
                        accountNames.get(record.getAccountId()),
                        categoryNames.get(record.getCategoryId())))
                .collect(Collectors.toList()));
        return Result.success(page);
    }

    /**
     * 收支概览（前端：financeFlowApi.getStatistics → {@code res || {...}}）
     */
    @GetMapping("/flow/statistics")
    public FinanceOverview flowStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return financeService.getOverview(null, parseStartTime(startDate), parseEndTime(endDate));
    }

    /**
     * 收支趋势（前端：financeFlowApi.getTrendData → {@code trendData || []}）
     */
    @GetMapping("/flow/trend")
    public List<Map<String, Object>> flowTrend(@RequestParam(defaultValue = "30") int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(days - 1, 0));
        FinanceTrend trend = financeService.getTrend(start, end);

        List<Map<String, Object>> result = new ArrayList<>();
        if (trend == null || trend.getDates() == null) {
            return result;
        }
        for (int i = 0; i < trend.getDates().size(); i++) {
            Map<String, Object> point = new HashMap<>();
            point.put("date", trend.getDates().get(i));
            point.put("income", valueAt(trend.getIncomes(), i));
            point.put("expense", valueAt(trend.getExpenses(), i));
            point.put("profit", valueAt(trend.getProfits(), i));
            result.add(point);
        }
        return result;
    }

    /**
     * 分类构成（前端：financeFlowApi.getCategoryData → {@code categoryData || []}）
     */
    @GetMapping("/flow/category")
    public List<Map<String, Object>> flowCategory(@RequestParam(required = false) String type) {
        LocalDateTime start = parseStartTime(null);
        LocalDateTime end = parseEndTime(null);
        List<CategoryStatistics> statistics = financeService.calculateCategoryStatistics(start, end);

        List<Map<String, Object>> result = new ArrayList<>();
        for (CategoryStatistics statistic : statistics) {
            Map<String, Object> item = new HashMap<>();
            item.put("categoryName", statistic.getCategoryName());
            item.put("amount", statistic.getNetAmount());
            item.put("income", statistic.getIncome());
            item.put("expense", statistic.getExpense());
            item.put("type", resolveCategoryType(statistic.getCategoryId()));
            result.add(item);
        }
        return result;
    }

    /**
     * 流水详情（前端：financeFlowApi.getFlowDetail）
     */
    @GetMapping("/flow/{id}")
    public Result<FinanceFlowVO> getFlow(@PathVariable Long id) {
        FinanceRecord record = financeService.getRecordById(id);
        if (record == null) {
            throw new BizException("流水不存在");
        }
        FinanceAccount account = financeService.getAccountById(record.getAccountId());
        FinanceCategory category = record.getCategoryId() == null
                ? null : financeService.getCategoryById(record.getCategoryId());
        return Result.success(FinanceConverter.toFlowVO(record,
                account == null ? null : account.getName(),
                category == null ? null : category.getName()));
    }

    /**
     * 创建流水（前端：financeFlowApi.createFlow）
     */
    @PostMapping("/flow")
    public Result<FinanceFlowVO> createFlow(@RequestBody FinanceFlowRequest request) {
        FinanceRecord record = FinanceConverter.toRecordEntity(request);
        Long id = financeService.recordFinance(record);
        FinanceRecord created = financeService.getRecordById(id);
        return Result.success(toFlowVO(created));
    }

    /**
     * 更新流水（前端：financeFlowApi.updateFlow）
     */
    @PutMapping("/flow")
    public Result<FinanceFlowVO> updateFlow(@RequestBody FinanceFlowRequest request) {
        if (request.getId() == null) {
            throw new BizException("流水 ID 不能为空");
        }
        FinanceRecord existing = financeService.getRecordById(request.getId());
        if (existing == null) {
            throw new BizException("流水不存在");
        }
        existing.setType(FinanceConverter.toFinanceType(request.getType()));
        existing.setAmount(request.getAmount());
        existing.setAccountId(request.getAccountId());
        existing.setCategoryId(request.getCategoryId());
        existing.setOrderNo(request.getOrderNo());
        existing.setRemark(request.getRemark());
        financeService.updateRecord(existing);
        return Result.success(toFlowVO(financeService.getRecordById(request.getId())));
    }

    /**
     * 删除流水（前端：financeFlowApi.deleteFlow）
     *
     * <p>删除会先回退账户余额，再把流水逻辑删除，使其不再出现在列表中。</p>
     */
    @DeleteMapping("/flow/{id}")
    public Result<Void> deleteFlow(@PathVariable Long id) {
        financeService.deleteRecord(id);
        return Result.success();
    }

    /**
     * 批量记账
     */
    @PostMapping("/flow/batch")
    public Result<BatchFinanceResult> batchRecordFinance(@RequestBody List<FinanceFlowRequest> requests) {
        List<FinanceRecord> records = requests.stream()
                .map(FinanceConverter::toRecordEntity)
                .collect(Collectors.toList());
        return Result.success(financeService.batchRecordFinance(records));
    }

    // ==================== 财务报表 ====================

    /**
     * 财务总览
     */
    @GetMapping("/overview")
    public Result<FinanceOverview> overview(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(financeService.getOverview(accountId,
                parseStartTime(startDate), parseEndTime(endDate)));
    }

    /**
     * 账户维度统计
     */
    @GetMapping("/statistics/account")
    public Result<List<AccountStatistics>> accountStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(financeService.getAccountStatistics(
                parseStartTime(startDate), parseEndTime(endDate)));
    }

    /**
     * 分类维度统计
     */
    @GetMapping("/statistics/category")
    public Result<List<CategoryStatistics>> categoryStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(financeService.calculateCategoryStatistics(
                parseStartTime(startDate), parseEndTime(endDate)));
    }

    /**
     * 日统计
     */
    @GetMapping("/statistics/daily")
    public Result<FinanceDailyStatistics> dailyStatistics(@RequestParam(required = false) String date) {
        LocalDate localDate = date == null || date.isEmpty() ? LocalDate.now() : LocalDate.parse(date);
        return Result.success(financeService.getDailyStatistics(localDate.atStartOfDay()));
    }

    /**
     * 月统计
     */
    @GetMapping("/statistics/monthly")
    public Result<List<FinanceMonthlyStatistics>> monthlyStatistics(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        int targetMonth = month == null ? LocalDate.now().getMonthValue() : month;
        return Result.success(financeService.getMonthlyStatistics(targetYear, targetMonth));
    }

    /**
     * 趋势分析
     */
    @GetMapping("/statistics/trend")
    public Result<FinanceTrend> trend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDate end = endDate == null || endDate.isEmpty() ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate start = startDate == null || startDate.isEmpty()
                ? end.minusDays(29) : LocalDate.parse(startDate);
        return Result.success(financeService.getTrend(start, end));
    }

    /**
     * 账户对账
     */
    @GetMapping("/reconcile")
    public Result<ReconciliationResult> reconcile(
            @RequestParam Long accountId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(financeService.reconcile(accountId,
                parseStartTime(startDate), parseEndTime(endDate)));
    }

    // ==================== 分类管理 ====================

    /**
     * 分类列表（前端：financeCategoryApi.getCategories → {@code res || []}）
     */
    @GetMapping("/category/list")
    public List<FinanceCategoryVO> listCategories(@RequestParam(required = false) String type) {
        CategoryType categoryType = null;
        if (type != null && !type.isEmpty()) {
            categoryType = FinanceConverter.toCategoryType(type);
        }
        return financeService.listCategories(categoryType).stream()
                .map(FinanceConverter::toCategoryVO)
                .collect(Collectors.toList());
    }

    /**
     * 创建分类（前端：financeCategoryApi.createCategory）
     */
    @PostMapping("/category")
    public Result<FinanceCategoryVO> createCategory(@RequestBody FinanceCategoryRequest request) {
        FinanceCategory category = FinanceConverter.toCategoryEntity(request);
        Long id = financeService.createCategory(category);
        return Result.success(FinanceConverter.toCategoryVO(financeService.getCategoryById(id)));
    }

    /**
     * 更新分类（前端：financeCategoryApi.updateCategory）
     */
    @PutMapping("/category")
    public Result<FinanceCategoryVO> updateCategory(@RequestBody FinanceCategoryRequest request) {
        if (request.getId() == null) {
            throw new BizException("分类 ID 不能为空");
        }
        FinanceCategory existing = financeService.getCategoryById(request.getId());
        if (existing == null) {
            throw new BizException("分类不存在");
        }
        existing.setName(request.getName());
        existing.setType(FinanceConverter.toCategoryType(request.getType()));
        existing.setParentId(request.getParentId());
        existing.setSortOrder(request.getSort() == null ? 0 : request.getSort());
        existing.setEnabled(request.getStatus() == null ? 1 : request.getStatus());
        existing.setRemark(request.getRemark());
        financeService.updateCategory(existing);
        return Result.success(FinanceConverter.toCategoryVO(financeService.getCategoryById(request.getId())));
    }

    /**
     * 删除分类（前端：financeCategoryApi.deleteCategory）
     */
    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        financeService.deleteCategory(id);
        return Result.success();
    }

    // ==================== 内部辅助 ====================

    private FinanceFlowVO toFlowVO(FinanceRecord record) {
        if (record == null) {
            return null;
        }
        FinanceAccount account = record.getAccountId() == null
                ? null : financeService.getAccountById(record.getAccountId());
        FinanceCategory category = record.getCategoryId() == null
                ? null : financeService.getCategoryById(record.getCategoryId());
        return FinanceConverter.toFlowVO(record,
                account == null ? null : account.getName(),
                category == null ? null : category.getName());
    }

    private Map<Long, String> accountNameMap() {
        return financeService.listAccounts().stream()
                .collect(Collectors.toMap(FinanceAccount::getId, FinanceAccount::getName,
                        (a, b) -> a));
    }

    private Map<Long, String> categoryNameMap() {
        return financeService.listCategories(null).stream()
                .collect(Collectors.toMap(FinanceCategory::getId, FinanceCategory::getName,
                        (a, b) -> a));
    }

    private String resolveCategoryType(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        FinanceCategory category = financeService.getCategoryById(categoryId);
        if (category == null || category.getType() == null) {
            return null;
        }
        return category.getType().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static BigDecimal valueAt(List<BigDecimal> values, int index) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return BigDecimal.ZERO;
        }
        return values.get(index);
    }

    private static FinanceType parseFinanceType(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        return FinanceConverter.toFinanceType(type);
    }

    /**
     * 起始时间：日期当天的 00:00:00
     */
    private static LocalDateTime parseStartTime(String date) {
        if (date == null || date.isEmpty()) {
            return null;
        }
        return LocalDate.parse(date).atStartOfDay();
    }

    /**
     * 截止时间：日期当天的 23:59:59
     */
    private static LocalDateTime parseEndTime(String date) {
        if (date == null || date.isEmpty()) {
            return null;
        }
        return LocalDate.parse(date).atTime(LocalTime.MAX);
    }
}
