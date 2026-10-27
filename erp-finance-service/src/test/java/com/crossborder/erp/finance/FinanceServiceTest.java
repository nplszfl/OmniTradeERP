package com.crossborder.erp.finance;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.finance.dto.FinanceRecordQuery;
import com.crossborder.erp.finance.entity.FinanceAccount;
import com.crossborder.erp.finance.entity.FinanceCategory;
import com.crossborder.erp.finance.entity.FinanceRecord;
import com.crossborder.erp.finance.enums.AccountStatus;
import com.crossborder.erp.finance.enums.CategoryType;
import com.crossborder.erp.finance.enums.FinanceType;
import com.crossborder.erp.finance.enums.OperationType;
import com.crossborder.erp.finance.enums.RecordStatus;
import com.crossborder.erp.finance.service.FinanceService;
import com.crossborder.erp.finance.vo.BatchFinanceResult;
import com.crossborder.erp.finance.vo.CategoryStatistics;
import com.crossborder.erp.finance.vo.FinanceDailyStatistics;
import com.crossborder.erp.finance.vo.FinanceMonthlyStatistics;
import com.crossborder.erp.finance.vo.FinanceOverview;
import com.crossborder.erp.finance.vo.FinanceTrend;
import com.crossborder.erp.finance.vo.ReconciliationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 财务服务集成单元测试
 *
 * <p>基于 H2 内存库 + MyBatis-Plus，真实执行 SQL 过滤与聚合逻辑。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public class FinanceServiceTest {

    @Autowired
    private FinanceService financeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    // ---------------- 辅助 ----------------

    /**
     * 金额断言：按数值比较，不受 BigDecimal scale 影响。
     *
     * <p>数据库金额列为 DECIMAL(18,2)，读回值形如 {@code 600.00}，
     * 而测试里写的是 {@code new BigDecimal("600")}，用 equals 比较会因为
     * scale 不同而误判为不相等。</p>
     */
    private void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual, "金额不应为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望金额 " + expected + "，实际 " + actual.toPlainString());
    }

    private FinanceAccount seedAccount(String name, String balance) {
        FinanceAccount account = new FinanceAccount();
        account.setName(name);
        account.setCode(name + "-CODE");
        account.setAccountType("BANK");
        account.setCurrency("USD");
        account.setBalance(new BigDecimal(balance));
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    private FinanceCategory seedCategory(String name, CategoryType type) {
        FinanceCategory category = new FinanceCategory();
        category.setName(name);
        category.setType(type);
        category.setSortOrder(1);
        category.setEnabled(1);
        return category;
    }

    private FinanceRecord seedRecord(Long accountId, FinanceType type, String amount,
                                     String remark, LocalDateTime createTime) {
        FinanceRecord record = new FinanceRecord();
        record.setRecordNo("SEED" + System.nanoTime() + Math.random());
        record.setAccountId(accountId);
        record.setType(type);
        record.setAmount(new BigDecimal(amount));
        record.setStatus(RecordStatus.COMPLETED);
        record.setRemark(remark);
        record.setCreateTime(createTime);
        record.setUpdateTime(createTime);
        return record;
    }

    // ---------------- 账户管理 ----------------

    @Test
    @DisplayName("创建账户：保留初始余额、状态启用、自动生成账户号")
    public void testCreateAccount() {
        FinanceAccount account = seedAccount("测试账户", "999");
        account.setRemark("单元测试");

        Long accountId = financeService.createAccount(account);

        assertNotNull(accountId);
        assertTrue(accountId > 0);
        // 前端账户表单包含「初始余额」字段，后端必须原样保留，不能静默清零
        assertAmount("999", account.getBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertNotNull(account.getAccountNo());
        assertTrue(account.getAccountNo().startsWith("ACC"));
        assertNotNull(account.getCreateTime());
        assertNotNull(financeService.getAccountById(accountId));
    }

    @Test
    @DisplayName("创建账户：账户名重复被拒绝")
    public void testCreateAccountDuplicateName() {
        financeService.createAccount(seedAccount("重复账户", "0"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.createAccount(seedAccount("重复账户", "0")));
        assertTrue(ex.getMessage().contains("账户名已存在"));
    }

    @Test
    @DisplayName("更新账户：改名成功且自身不触发重名校验")
    public void testUpdateAccount() {
        Long accountId = financeService.createAccount(seedAccount("待更新账户", "100"));

        FinanceAccount update = financeService.getAccountById(accountId);
        update.setName("已更新账户");
        financeService.updateAccount(update);

        FinanceAccount updated = financeService.getAccountById(accountId);
        assertEquals("已更新账户", updated.getName());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    @DisplayName("删除账户：有余额时拒绝")
    public void testDeleteAccountWithBalance() {
        Long accountId = financeService.createAccount(seedAccount("有余额账户", "50"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.deleteAccount(accountId));
        assertTrue(ex.getMessage().contains("余额"));
    }

    @Test
    @DisplayName("删除账户：有流水记录时拒绝")
    public void testDeleteAccountWithRecords() {
        Long accountId = financeService.createAccount(seedAccount("有流水账户", "0"));
        financeService.recordFinance(seedRecord(accountId, FinanceType.INCOME, "10", "流水", LocalDateTime.now()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.deleteAccount(accountId));
        assertTrue(ex.getMessage().contains("流水"));
    }

    @Test
    @DisplayName("删除账户：无余额无流水时置为已删除")
    public void testDeleteAccountSuccess() {
        Long accountId = financeService.createAccount(seedAccount("可删除账户", "0"));

        financeService.deleteAccount(accountId);

        assertEquals(AccountStatus.DELETED, financeService.getAccountById(accountId).getStatus());
    }

    @Test
    @DisplayName("调整余额：增加操作累加余额")
    public void testUpdateAccountBalanceIncrease() {
        Long accountId = financeService.createAccount(seedAccount("加钱账户", "100"));

        financeService.updateAccountBalance(accountId, new BigDecimal("50"), OperationType.INCREASE);

        assertAmount("150", financeService.getAccountById(accountId).getBalance());
    }

    @Test
    @DisplayName("调整余额：扣减超过余额时拒绝")
    public void testUpdateAccountBalanceInsufficient() {
        Long accountId = financeService.createAccount(seedAccount("余额不足账户", "10"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.updateAccountBalance(accountId, new BigDecimal("100"), OperationType.DECREASE));
        assertTrue(ex.getMessage().contains("余额不足"));
    }

    @Test
    @DisplayName("转账：生成一出一入两条流水并调整双方余额")
    public void testTransferBetweenAccounts() {
        Long fromId = financeService.createAccount(seedAccount("转出账户", "500"));
        Long toId = financeService.createAccount(seedAccount("转入账户", "100"));

        financeService.transferBetweenAccounts(fromId, toId, new BigDecimal("200"), "货款结算");

        assertAmount("300", financeService.getAccountById(fromId).getBalance());
        assertAmount("300", financeService.getAccountById(toId).getBalance());

        Page<FinanceRecord> page = financeService.listRecords(new FinanceRecordQuery());
        assertEquals(2, page.getTotal());
    }

    @Test
    @DisplayName("转账：转出账户余额不足时拒绝")
    public void testTransferInsufficientBalance() {
        Long fromId = financeService.createAccount(seedAccount("没钱账户", "10"));
        Long toId = financeService.createAccount(seedAccount("目标账户", "0"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.transferBetweenAccounts(fromId, toId, new BigDecimal("100"), "备注"));
        assertTrue(ex.getMessage().contains("余额不足"));
    }

    @Test
    @DisplayName("转账：不能转到同一账户")
    public void testTransferToSameAccount() {
        Long accountId = financeService.createAccount(seedAccount("同一账户", "100"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.transferBetweenAccounts(accountId, accountId, new BigDecimal("10"), "备注"));
        assertTrue(ex.getMessage().contains("同一账户"));
    }

    // ---------------- 记账 ----------------

    @Test
    @DisplayName("记录收入：账户余额增加并写入期初期末余额")
    public void testRecordIncome() {
        Long accountId = financeService.createAccount(seedAccount("收入账户", "100"));

        FinanceRecord record = seedRecord(accountId, FinanceType.INCOME, "500", "测试收入", LocalDateTime.now());
        Long recordId = financeService.recordFinance(record);

        assertNotNull(recordId);
        assertTrue(recordId > 0);
        assertAmount("600", financeService.getAccountById(accountId).getBalance());
        assertAmount("100", record.getBeforeBalance());
        assertAmount("600", record.getAfterBalance());
        assertEquals(RecordStatus.COMPLETED, record.getStatus());
        assertTrue(record.getRecordNo().startsWith("FIN"));
    }

    @Test
    @DisplayName("记录支出：账户余额减少")
    public void testRecordExpense() {
        Long accountId = financeService.createAccount(seedAccount("支出账户", "100"));

        FinanceRecord record = seedRecord(accountId, FinanceType.EXPENSE, "30", "测试支出", LocalDateTime.now());
        financeService.recordFinance(record);

        assertAmount("70", financeService.getAccountById(accountId).getBalance());
        assertAmount("30", record.getBeforeBalance().subtract(record.getAfterBalance()));
    }

    @Test
    @DisplayName("记录退款：账户余额同样增加")
    public void testRecordRefund() {
        Long accountId = financeService.createAccount(seedAccount("退款账户", "100"));

        FinanceRecord record = seedRecord(accountId, FinanceType.REFUND, "20", "退款", LocalDateTime.now());
        financeService.recordFinance(record);

        assertAmount("120", financeService.getAccountById(accountId).getBalance());
    }

    @Test
    @DisplayName("记账：账户不存在时拒绝")
    public void testRecordAccountNotFound() {
        FinanceRecord record = seedRecord(999999L, FinanceType.INCOME, "10", "无账户", LocalDateTime.now());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.recordFinance(record));
        assertTrue(ex.getMessage().contains("账户不存在"));
    }

    @Test
    @DisplayName("按流水号查询记录")
    public void testGetRecordByNo() {
        Long accountId = financeService.createAccount(seedAccount("查询账户", "0"));
        FinanceRecord record = seedRecord(accountId, FinanceType.INCOME, "10", "备注", LocalDateTime.now());
        Long recordId = financeService.recordFinance(record);

        FinanceRecord found = financeService.getRecordByNo(record.getRecordNo());
        assertNotNull(found);
        assertEquals(recordId, found.getId());
    }

    @Test
    @DisplayName("分页查询记录：按类型与账户过滤")
    public void testListRecords() {
        Long accountId = financeService.createAccount(seedAccount("分页账户", "0"));
        Long otherId = financeService.createAccount(seedAccount("另一个账户", "0"));
        financeService.recordFinance(seedRecord(accountId, FinanceType.INCOME, "10", "收入备注", LocalDateTime.now()));
        financeService.recordFinance(seedRecord(accountId, FinanceType.EXPENSE, "5", "支出备注", LocalDateTime.now()));
        financeService.recordFinance(seedRecord(otherId, FinanceType.INCOME, "8", "他人备注", LocalDateTime.now()));

        FinanceRecordQuery query = new FinanceRecordQuery();
        query.setAccountId(accountId);
        Page<FinanceRecord> page = financeService.listRecords(query);
        assertEquals(2, page.getTotal());

        query.setType(FinanceType.INCOME);
        assertEquals(1, financeService.listRecords(query).getTotal());

        query.setType(null);
        query.setKeyword("收入备注");
        assertEquals(1, financeService.listRecords(query).getTotal());
    }

    @Test
    @DisplayName("更新记录：金额变化时回滚并重算账户余额")
    public void testUpdateRecordAdjustsBalance() {
        Long accountId = financeService.createAccount(seedAccount("调整账户", "500"));
        FinanceRecord record = seedRecord(accountId, FinanceType.INCOME, "100", "原备注", LocalDateTime.now());
        Long recordId = financeService.recordFinance(record);

        FinanceRecord update = financeService.getRecordById(recordId);
        update.setAmount(new BigDecimal("300"));
        financeService.updateRecord(update);

        // 原记录收入 100 被回滚（600-100=500），新金额 300 计入（500+300=800）
        assertAmount("800", financeService.getAccountById(accountId).getBalance());
    }

    @Test
    @DisplayName("更新记录：已作废记录不允许修改")
    public void testUpdateVoidedRecordRejected() {
        Long accountId = financeService.createAccount(seedAccount("作废账户", "0"));
        FinanceRecord record = seedRecord(accountId, FinanceType.INCOME, "100", "备注", LocalDateTime.now());
        Long recordId = financeService.recordFinance(record);
        financeService.voidRecord(recordId, "先作废");

        FinanceRecord update = financeService.getRecordById(recordId);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.updateRecord(update));
        assertTrue(ex.getMessage().contains("已作废"));
    }

    @Test
    @DisplayName("作废记录：回退余额并置为已作废")
    public void testVoidRecord() {
        Long accountId = financeService.createAccount(seedAccount("作废测试账户", "500"));
        FinanceRecord record = seedRecord(accountId, FinanceType.INCOME, "100", "备注", LocalDateTime.now());
        Long recordId = financeService.recordFinance(record);

        financeService.voidRecord(recordId, "录入错误");

        assertAmount("500", financeService.getAccountById(accountId).getBalance());
        FinanceRecord voided = financeService.getRecordById(recordId);
        assertEquals(RecordStatus.VOIDED, voided.getStatus());
        assertEquals("录入错误", voided.getVoidReason());
        assertNotNull(voided.getVoidTime());
    }

    @Test
    @DisplayName("作废记录：重复作废被拒绝")
    public void testVoidRecordTwice() {
        Long accountId = financeService.createAccount(seedAccount("重复作废账户", "500"));
        FinanceRecord record = seedRecord(accountId, FinanceType.INCOME, "100", "备注", LocalDateTime.now());
        Long recordId = financeService.recordFinance(record);
        financeService.voidRecord(recordId, "第一次");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.voidRecord(recordId, "再作废"));
        assertTrue(ex.getMessage().contains("已作废"));
    }

    // ---------------- 分类管理 ----------------

    @Test
    @DisplayName("创建分类：同类型下重名被拒绝")
    public void testCreateCategoryDuplicate() {
        financeService.createCategory(seedCategory("重复分类", CategoryType.EXPENSE));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.createCategory(seedCategory("重复分类", CategoryType.EXPENSE)));
        assertTrue(ex.getMessage().contains("分类名称已存在"));
    }

    @Test
    @DisplayName("创建分类：成功并可按类型筛选")
    public void testCreateCategory() {
        Long id = financeService.createCategory(seedCategory("广告推广", CategoryType.EXPENSE));

        assertNotNull(id);
        assertTrue(id > 0);
        assertEquals(1, financeService.listCategories(CategoryType.EXPENSE).size());
        assertEquals(0, financeService.listCategories(CategoryType.INCOME).size());
        assertEquals(1, financeService.listCategories(null).size());
    }

    @Test
    @DisplayName("删除分类：分类下有流水时拒绝")
    public void testDeleteCategoryWithRecords() {
        Long categoryId = financeService.createCategory(seedCategory("有流水分类", CategoryType.INCOME));
        Long accountId = financeService.createAccount(seedAccount("分类流水账户", "0"));

        FinanceRecord record = seedRecord(accountId, FinanceType.INCOME, "50", "备注", LocalDateTime.now());
        record.setCategoryId(categoryId);
        financeService.recordFinance(record);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> financeService.deleteCategory(categoryId));
        assertTrue(ex.getMessage().contains("流水"));
    }

    @Test
    @DisplayName("删除分类：无流水时成功")
    public void testDeleteCategorySuccess() {
        Long categoryId = financeService.createCategory(seedCategory("可删分类", CategoryType.INCOME));

        financeService.deleteCategory(categoryId);

        assertEquals(0, financeService.listCategories(null).size());
    }

    // ---------------- 报表与统计 ----------------

    @Test
    @DisplayName("区间收入合计与利润")
    public void testCalculateIncomeAndProfit() {
        Long accountId = financeService.createAccount(seedAccount("统计账户", "0"));
        financeService.recordFinance(seedRecord(accountId, FinanceType.INCOME, "1000", "收入", LocalDateTime.now()));
        financeService.recordFinance(seedRecord(accountId, FinanceType.EXPENSE, "300", "支出", LocalDateTime.now()));

        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        assertAmount("1000", financeService.calculateTotalIncome(start, end));
        assertAmount("300", financeService.calculateTotalExpense(start, end));
        assertAmount("700", financeService.calculateProfit(start, end));
    }

    @Test
    @DisplayName("分类维度统计：按净额降序")
    public void testCalculateCategoryStatistics() {
        Long accountId = financeService.createAccount(seedAccount("分类统计账户", "0"));
        Long cat1 = financeService.createCategory(seedCategory("销售收入", CategoryType.INCOME));
        Long cat2 = financeService.createCategory(seedCategory("物流费用", CategoryType.EXPENSE));

        FinanceRecord r1 = seedRecord(accountId, FinanceType.INCOME, "500", "收入", LocalDateTime.now());
        r1.setCategoryId(cat1);
        financeService.recordFinance(r1);

        FinanceRecord r2 = seedRecord(accountId, FinanceType.EXPENSE, "100", "支出", LocalDateTime.now());
        r2.setCategoryId(cat2);
        financeService.recordFinance(r2);

        List<CategoryStatistics> stats = financeService.calculateCategoryStatistics(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertEquals(2, stats.size());
        assertEquals("销售收入", stats.get(0).getCategoryName());
        assertAmount("500", stats.get(0).getIncome());
        assertAmount("500", stats.get(0).getNetAmount());
        assertEquals(1, stats.get(0).getRecordCount());
    }

    @Test
    @DisplayName("日统计：收入减支出得利润")
    public void testDailyStatistics() {
        Long accountId = financeService.createAccount(seedAccount("日统计账户", "0"));
        financeService.recordFinance(seedRecord(accountId, FinanceType.INCOME, "200", "收入", LocalDateTime.now()));
        financeService.recordFinance(seedRecord(accountId, FinanceType.EXPENSE, "50", "支出", LocalDateTime.now()));

        FinanceDailyStatistics stats = financeService.getDailyStatistics(LocalDateTime.now());

        assertNotNull(stats);
        assertAmount("200", stats.getIncome());
        assertAmount("50", stats.getExpense());
        assertAmount("150", stats.getProfit());
        assertEquals(2, stats.getRecordCount());
    }

    @Test
    @DisplayName("区间逐日统计：返回区间内每一天")
    public void testDailyStatisticsList() {
        List<FinanceDailyStatistics> list = financeService.getDailyStatisticsList(
                LocalDate.now().minusDays(2), LocalDate.now());

        assertEquals(3, list.size());
    }

    @Test
    @DisplayName("月度统计：汇总全月并附分类统计")
    public void testMonthlyStatistics() {
        Long accountId = financeService.createAccount(seedAccount("月统计账户", "0"));
        financeService.recordFinance(seedRecord(accountId, FinanceType.INCOME, "1000", "收入", LocalDateTime.now()));
        financeService.recordFinance(seedRecord(accountId, FinanceType.EXPENSE, "400", "支出", LocalDateTime.now()));

        List<FinanceMonthlyStatistics> list = financeService.getMonthlyStatistics(
                LocalDate.now().getYear(), LocalDate.now().getMonthValue());

        assertEquals(1, list.size());
        FinanceMonthlyStatistics stats = list.get(0);
        assertAmount("1000", stats.getTotalIncome());
        assertAmount("400", stats.getTotalExpense());
        assertAmount("600", stats.getTotalProfit());
        assertNotNull(stats.getAvgDailyIncome());
        assertNotNull(stats.getCategoryStatistics());
    }

    @Test
    @DisplayName("财务总览：按账户统计收入支出与利润率")
    public void testOverview() {
        Long accountId = financeService.createAccount(seedAccount("总览账户", "0"));
        financeService.recordFinance(seedRecord(accountId, FinanceType.INCOME, "1000", "收入", LocalDateTime.now()));
        financeService.recordFinance(seedRecord(accountId, FinanceType.EXPENSE, "250", "支出", LocalDateTime.now()));

        FinanceOverview overview = financeService.getOverview(accountId,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertNotNull(overview);
        assertAmount("1000", overview.getTotalIncome());
        assertAmount("250", overview.getTotalExpense());
        assertAmount("750", overview.getProfit());
        assertEquals(0, new BigDecimal("75").compareTo(overview.getProfitRate()));
        assertEquals(2, overview.getRecordCount());
    }

    @Test
    @DisplayName("账户维度统计：逐账户列出收支与净额")
    public void testAccountStatistics() {
        Long accountId = financeService.createAccount(seedAccount("统计账户", "0"));
        financeService.recordFinance(seedRecord(accountId, FinanceType.INCOME, "1000", "收入", LocalDateTime.now()));
        financeService.recordFinance(seedRecord(accountId, FinanceType.EXPENSE, "250", "支出", LocalDateTime.now()));

        var stats = financeService.getAccountStatistics(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertEquals(1, stats.size());
        assertEquals("统计账户", stats.get(0).getAccountName());
        assertAmount("750", stats.get(0).getNetAmount());
        assertEquals(2, stats.get(0).getRecordCount());
    }

    @Test
    @DisplayName("趋势分析：返回日期与三条金额序列")
    public void testTrend() {
        FinanceTrend trend = financeService.getTrend(LocalDate.now().minusDays(2), LocalDate.now());

        assertNotNull(trend);
        assertEquals(3, trend.getDates().size());
        assertEquals(3, trend.getIncomes().size());
        assertEquals(3, trend.getExpenses().size());
        assertEquals(3, trend.getProfits().size());
    }

    // ---------------- 对账与批量 ----------------

    @Test
    @DisplayName("对账：余额连续且与账户一致时判定平账")
    public void testReconcileBalanced() {
        Long accountId = financeService.createAccount(seedAccount("对账账户", "100"));

        FinanceRecord r1 = seedRecord(accountId, FinanceType.INCOME, "50", "收入", LocalDateTime.now());
        r1.setBeforeBalance(new BigDecimal("100"));
        r1.setAfterBalance(new BigDecimal("150"));
        financeService.recordFinance(r1);

        FinanceRecord r2 = seedRecord(accountId, FinanceType.EXPENSE, "30", "支出", LocalDateTime.now());
        r2.setBeforeBalance(new BigDecimal("150"));
        r2.setAfterBalance(new BigDecimal("120"));
        financeService.recordFinance(r2);

        ReconciliationResult result = financeService.reconcile(accountId,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertNotNull(result);
        assertTrue(result.getBalanced());
        assertTrue(result.getErrors().isEmpty());
        assertEquals(2, result.getRecordCount());
        assertAmount("120", result.getCalculatedBalance());
    }

    @Test
    @DisplayName("对账：账户余额与流水不一致时报告差异")
    public void testReconcileUnbalanced() {
        Long accountId = financeService.createAccount(seedAccount("对账差异账户", "999"));

        FinanceRecord r1 = seedRecord(accountId, FinanceType.INCOME, "50", "收入", LocalDateTime.now());
        r1.setBeforeBalance(new BigDecimal("0"));
        r1.setAfterBalance(new BigDecimal("50"));
        financeService.recordFinance(r1);

        ReconciliationResult result = financeService.reconcile(accountId,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertFalse(result.getBalanced());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("批量记账：成功与失败分别计数")
    public void testBatchRecordFinance() {
        Long accountId = financeService.createAccount(seedAccount("批量账户", "0"));

        FinanceRecord ok = seedRecord(accountId, FinanceType.INCOME, "100", "BATCH-OK", LocalDateTime.now());
        FinanceRecord bad = seedRecord(999999L, FinanceType.INCOME, "100", "BATCH-BAD", LocalDateTime.now());

        BatchFinanceResult result = financeService.batchRecordFinance(List.of(ok, bad));

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(1, result.getSuccessIds().size());
        assertEquals(1, result.getFailedReasons().size());
        assertTrue(result.getFailedReasons().get(0).contains("BATCH-BAD"));
    }

    // ---------------- 枚举 ----------------

    @Test
    @DisplayName("财务枚举：可按名称解析描述")
    public void testEnums() {
        assertEquals(FinanceType.INCOME, FinanceType.getByCode("INCOME"));
        assertEquals("收入", FinanceType.getDescription("INCOME"));
        assertNull(FinanceType.getByCode("NOT_EXIST"));

        assertEquals(RecordStatus.COMPLETED, RecordStatus.getByCode("COMPLETED"));
        assertEquals("已完成", RecordStatus.getDescription("COMPLETED"));

        assertEquals(AccountStatus.ACTIVE, AccountStatus.getByCode("ACTIVE"));
        assertEquals("启用", AccountStatus.getDescription("ACTIVE"));

        assertEquals(OperationType.INCREASE, OperationType.getByCode("INCREASE"));
        assertEquals("增加", OperationType.getDescription("INCREASE"));

        assertEquals(CategoryType.EXPENSE, CategoryType.getByCode("EXPENSE"));
        assertEquals("支出类", CategoryType.getDescription("EXPENSE"));
    }
}
