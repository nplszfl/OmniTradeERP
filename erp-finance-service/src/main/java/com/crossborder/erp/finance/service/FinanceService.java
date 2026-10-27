package com.crossborder.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.finance.dto.FinanceRecordQuery;
import com.crossborder.erp.finance.entity.FinanceAccount;
import com.crossborder.erp.finance.entity.FinanceCategory;
import com.crossborder.erp.finance.entity.FinanceRecord;
import com.crossborder.erp.finance.enums.CategoryType;
import com.crossborder.erp.finance.enums.OperationType;
import com.crossborder.erp.finance.vo.AccountStatistics;
import com.crossborder.erp.finance.vo.BatchFinanceResult;
import com.crossborder.erp.finance.vo.CategoryStatistics;
import com.crossborder.erp.finance.vo.FinanceDailyStatistics;
import com.crossborder.erp.finance.vo.FinanceMonthlyStatistics;
import com.crossborder.erp.finance.vo.FinanceOverview;
import com.crossborder.erp.finance.vo.FinanceTrend;
import com.crossborder.erp.finance.vo.ReconciliationResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 财务服务接口
 *
 * <p>负责：财务流水记账、账户管理、分类管理、报表统计、趋势分析、对账与批量记账。</p>
 */
public interface FinanceService {

    // ==================== 财务记录管理 ====================

    /**
     * 记录一笔财务流水，同时更新账户余额
     */
    Long recordFinance(FinanceRecord record);

    /**
     * 按 ID 查询流水
     */
    FinanceRecord getRecordById(Long id);

    /**
     * 按流水号查询流水
     */
    FinanceRecord getRecordByNo(String recordNo);

    /**
     * 分页查询流水
     */
    Page<FinanceRecord> listRecords(FinanceRecordQuery query);

    /**
     * 更新流水；若金额 / 类型 / 账户发生变化则调整对应账户余额
     */
    void updateRecord(FinanceRecord record);

    /**
     * 作废流水并回退账户余额
     */
    void voidRecord(Long recordId, String voidReason);

    /**
     * 删除流水：回退账户余额后逻辑删除该流水。
     *
     * <p>与「作废」的区别在于：作废保留流水痕迹（状态置为 VOIDED），
     * 删除则让流水不再出现在列表中，但两者都必须把账户余额改回去。</p>
     */
    void deleteRecord(Long recordId);

    // ==================== 账户管理 ====================

    /**
     * 列出全部账户
     */
    List<FinanceAccount> listAccounts();

    /**
     * 按 ID 查询账户
     */
    FinanceAccount getAccountById(Long id);

    /**
     * 创建账户
     */
    Long createAccount(FinanceAccount account);

    /**
     * 更新账户
     */
    void updateAccount(FinanceAccount account);

    /**
     * 删除账户（有余额或有流水时拒绝）
     */
    void deleteAccount(Long accountId);

    /**
     * 设为默认账户：同一时刻只允许一个默认账户。
     */
    void setDefaultAccount(Long accountId);

    /**
     * 直接调整账户余额
     */
    void updateAccountBalance(Long accountId, BigDecimal amount, OperationType operation);

    /**
     * 账户间转账，生成一出一入两条流水
     */
    void transferBetweenAccounts(Long fromAccountId, Long toAccountId, BigDecimal amount, String remark);

    // ==================== 分类管理 ====================

    /**
     * 按类型列出分类
     */
    List<FinanceCategory> listCategories(CategoryType type);

    /**
     * 按 ID 查询分类
     */
    FinanceCategory getCategoryById(Long id);

    /**
     * 创建分类
     */
    Long createCategory(FinanceCategory category);

    /**
     * 更新分类
     */
    void updateCategory(FinanceCategory category);

    /**
     * 删除分类（分类下有流水时拒绝）
     */
    void deleteCategory(Long categoryId);

    // ==================== 财务报表 ====================

    /**
     * 统计区间收入合计
     */
    BigDecimal calculateTotalIncome(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计区间支出合计
     */
    BigDecimal calculateTotalExpense(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计区间利润
     */
    BigDecimal calculateProfit(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按分类维度统计
     */
    List<CategoryStatistics> calculateCategoryStatistics(LocalDateTime startTime, LocalDateTime endTime);

    // ==================== 统计分析 ====================

    /**
     * 某日统计
     */
    FinanceDailyStatistics getDailyStatistics(LocalDateTime date);

    /**
     * 区间逐日统计
     */
    List<FinanceDailyStatistics> getDailyStatisticsList(LocalDate startDate, LocalDate endDate);

    /**
     * 月度统计
     */
    List<FinanceMonthlyStatistics> getMonthlyStatistics(Integer year, Integer month);

    /**
     * 财务总览
     */
    FinanceOverview getOverview(Long accountId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 账户维度统计
     */
    List<AccountStatistics> getAccountStatistics(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 趋势分析
     */
    FinanceTrend getTrend(LocalDate startDate, LocalDate endDate);

    // ==================== 对账与批量 ====================

    /**
     * 对账：校验流水余额连续性与账户余额一致性
     */
    ReconciliationResult reconcile(Long accountId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 批量记账，返回成功 / 失败明细
     */
    BatchFinanceResult batchRecordFinance(List<FinanceRecord> records);
}
