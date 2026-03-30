package com.crossborder.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.finance.entity.*;
import com.crossborder.erp.finance.enums.*;
import com.crossborder.erp.finance.mapper.*;
import com.crossborder.erp.finance.service.FinanceService;
import com.crossborder.erp.finance.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 财务服务实现 - 完整版
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final FinanceRecordMapper recordMapper;
    private final FinanceAccountMapper accountMapper;
    private final FinanceCategoryMapper categoryMapper;
    private final FinanceStatisticsMapper statisticsMapper;

    // ==================== 财务记录管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordFinance(FinanceRecord record) {
        record.setRecordNo(generateRecordNo());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        
        // 获取账户
        FinanceAccount account = accountMapper.selectById(record.getAccountId());
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        
        BigDecimal oldBalance = account.getBalance();
        
        // 计算交易后的余额
        if (record.getType() == FinanceType.INCOME) {
            BigDecimal newBalance = oldBalance.add(record.getAmount());
            record.setAfterBalance(newBalance);
            account.setBalance(newBalance);
        } else if (record.getType() == FinanceType.EXPENSE) {
            BigDecimal newBalance = oldBalance.subtract(record.getAmount());
            record.setAfterBalance(newBalance);
            account.setBalance(newBalance);
        } else { // 退款
            BigDecimal newBalance = oldBalance.add(record.getAmount());
            record.setAfterBalance(newBalance);
            account.setBalance(newBalance);
        }
        
        record.setBeforeBalance(oldBalance);
        record.setStatus(RecordStatus.COMPLETED);
        account.setUpdateTime(LocalDateTime.now());
        
        // 保存财务记录
        recordMapper.insert(record);
        // 更新账户余额
        accountMapper.updateById(account);
        
        // 更新账户统计
        updateAccountStatistics(account.getId(), record);
        
        log.info("记录财务成功: {} {} {}", record.getType().getDescription(), record.getAmount(), record.getRecordNo());
        return record.getId();
    }

    @Override
    public FinanceRecord getRecordById(Long id) {
        return recordMapper.selectById(id);
    }

    @Override
    public FinanceRecord getRecordByNo(String recordNo) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getRecordNo, recordNo);
        return recordMapper.selectOne(wrapper);
    }

    @Override
    public Page<FinanceRecord> listRecords(FinanceRecordQuery query) {
        Page<FinanceRecord> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        
        if (query.getAccountId() != null) {
            wrapper.eq(FinanceRecord::getAccountId, query.getAccountId());
        }
        if (query.getType() != null) {
            wrapper.eq(FinanceRecord::getType, query.getType());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(FinanceRecord::getCategoryId, query.getCategoryId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(FinanceRecord::getStatus, query.getStatus());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(FinanceRecord::getCreateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(FinanceRecord::getCreateTime, query.getEndTime());
        }
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(FinanceRecord::getRecordNo, query.getKeyword())
                    .or().like(FinanceRecord::getOrderNo, query.getKeyword())
                    .or().like(FinanceRecord::getRemark, query.getKeyword()));
        }
        
        wrapper.orderByDesc(FinanceRecord::getCreateTime);
        return recordMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRecord(FinanceRecord record) {
        FinanceRecord oldRecord = recordMapper.selectById(record.getId());
        if (oldRecord == null) {
            throw new RuntimeException("记录不存在");
        }
        if (oldRecord.getStatus() == RecordStatus.VOIDED) {
            throw new RuntimeException("已作废的记录不能修改");
        }
        
        // 如果金额或类型发生变化，需要调整账户余额
        if (!oldRecord.getAmount().equals(record.getAmount()) || 
            oldRecord.getType() != record.getType() ||
            !oldRecord.getAccountId().equals(record.getAccountId())) {
            
            // 恢复原账户余额
            FinanceAccount oldAccount = accountMapper.selectById(oldRecord.getAccountId());
            if (oldRecord.getType() == FinanceType.INCOME) {
                oldAccount.setBalance(oldAccount.getBalance().subtract(oldRecord.getAmount()));
            } else {
                oldAccount.setBalance(oldAccount.getBalance().add(oldRecord.getAmount()));
            }
            accountMapper.updateById(oldAccount);
            
            // 更新新账户余额
            FinanceAccount newAccount = accountMapper.selectById(record.getAccountId());
            if (record.getType() == FinanceType.INCOME) {
                newAccount.setBalance(newAccount.getBalance().add(record.getAmount()));
            } else {
                newAccount.setBalance(newAccount.getBalance().subtract(record.getAmount()));
            }
            accountMapper.updateById(newAccount);
        }
        
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        
        log.info("更新财务记录: {}", record.getRecordNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidRecord(Long recordId, String voidReason) {
        FinanceRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new RuntimeException("记录不存在");
        }
        if (record.getStatus() == RecordStatus.VOIDED) {
            throw new RuntimeException("记录已作废");
        }
        
        // 调整账户余额
        FinanceAccount account = accountMapper.selectById(record.getAccountId());
        if (record.getType() == FinanceType.INCOME) {
            account.setBalance(account.getBalance().subtract(record.getAmount()));
        } else {
            account.setBalance(account.getBalance().add(record.getAmount()));
        }
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
        
        // 更新记录状态
        record.setStatus(RecordStatus.VOIDED);
        record.setVoidTime(LocalDateTime.now());
        record.setVoidReason(voidReason);
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        
        log.info("作废财务记录: {}, 原因: {}", record.getRecordNo(), voidReason);
    }

    // ==================== 账户管理 ====================

    @Override
    public List<FinanceAccount> listAccounts() {
        LambdaQueryWrapper<FinanceAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(FinanceAccount::getCreateTime);
        return accountMapper.selectList(wrapper);
    }

    @Override
    public FinanceAccount getAccountById(Long id) {
        return accountMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAccount(FinanceAccount account) {
        // 检查账户名是否重复
        LambdaQueryWrapper<FinanceAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceAccount::getName, account.getName());
        if (accountMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("账户名已存在");
        }
        
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountNo(generateAccountNo());
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.insert(account);
        
        log.info("创建账户成功: {}", account.getName());
        return account.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccount(FinanceAccount account) {
        FinanceAccount oldAccount = accountMapper.selectById(account.getId());
        if (oldAccount == null) {
            throw new RuntimeException("账户不存在");
        }
        
        // 检查名称重复
        LambdaQueryWrapper<FinanceAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceAccount::getName, account.getName())
               .ne(FinanceAccount::getId, account.getId());
        if (accountMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("账户名已存在");
        }
        
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
        
        log.info("更新账户成功: {}", account.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long accountId) {
        FinanceAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        
        // 检查是否有余额
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new RuntimeException("账户还有余额，无法删除");
        }
        
        // 检查是否有流水记录
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getAccountId, accountId);
        if (recordMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("账户有流水记录，无法删除");
        }
        
        account.setStatus(AccountStatus.DELETED);
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
        
        log.info("删除账户: {}", account.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccountBalance(Long accountId, BigDecimal amount, OperationType operation) {
        FinanceAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        
        if (operation == OperationType.INCREASE) {
            account.setBalance(account.getBalance().add(amount));
        } else {
            if (account.getBalance().compareTo(amount) < 0) {
                throw new RuntimeException("账户余额不足");
            }
            account.setBalance(account.getBalance().subtract(amount));
        }
        
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
        
        log.info("更新账户余额: {} {}, {}", account.getName(), operation, amount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferBetweenAccounts(Long fromAccountId, Long toAccountId, 
                                        BigDecimal amount, String remark) {
        if (fromAccountId.equals(toAccountId)) {
            throw new RuntimeException("不能转账到同一账户");
        }
        
        FinanceAccount fromAccount = accountMapper.selectById(fromAccountId);
        FinanceAccount toAccount = accountMapper.selectById(toAccountId);
        
        if (fromAccount == null || toAccount == null) {
            throw new RuntimeException("账户不存在");
        }
        
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足");
        }
        
        // 扣减转出账户
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        fromAccount.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(fromAccount);
        
        // 增加转入账户
        toAccount.setBalance(toAccount.getBalance().add(amount));
        toAccount.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(toAccount);
        
        // 记录转账记录
        FinanceRecord transferRecord = new FinanceRecord();
        transferRecord.setRecordNo(generateRecordNo());
        transferRecord.setAccountId(fromAccountId);
        transferRecord.setType(FinanceType.EXPENSE);
        transferRecord.setAmount(amount);
        transferRecord.setBeforeBalance(fromAccount.getBalance().add(amount));
        transferRecord.setAfterBalance(fromAccount.getBalance());
        transferRecord.setRemark("转账转出: " + remark);
        transferRecord.setStatus(RecordStatus.COMPLETED);
        transferRecord.setCreateTime(LocalDateTime.now());
        transferRecord.setUpdateTime(LocalDateTime.now());
        recordMapper.insert(transferRecord);
        
        FinanceRecord incomeRecord = new FinanceRecord();
        incomeRecord.setRecordNo(generateRecordNo());
        incomeRecord.setAccountId(toAccountId);
        incomeRecord.setType(FinanceType.INCOME);
        incomeRecord.setAmount(amount);
        incomeRecord.setBeforeBalance(toAccount.getBalance().subtract(amount));
        incomeRecord.setAfterBalance(toAccount.getBalance());
        incomeRecord.setRemark("转账收入: " + remark);
        incomeRecord.setStatus(RecordStatus.COMPLETED);
        incomeRecord.setCreateTime(LocalDateTime.now());
        incomeRecord.setUpdateTime(LocalDateTime.now());
        recordMapper.insert(incomeRecord);
        
        log.info("账户转账: {} -> {}, 金额: {}", fromAccountId, toAccountId, amount);
    }

    // ==================== 分类管理 ====================

    @Override
    public List<FinanceCategory> listCategories(CategoryType type) {
        LambdaQueryWrapper<FinanceCategory> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(FinanceCategory::getType, type);
        }
        wrapper.orderByAsc(FinanceCategory::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(FinanceCategory category) {
        // 检查名称重复
        LambdaQueryWrapper<FinanceCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceCategory::getName, category.getName())
               .eq(FinanceCategory::getType, category.getType());
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("分类名称已存在");
        }
        
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.insert(category);
        
        log.info("创建分类成功: {}", category.getName());
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(FinanceCategory category) {
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.updateById(category);
        
        log.info("更新分类成功: {}", category.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long categoryId) {
        // 检查是否有记录使用该分类
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getCategoryId, categoryId);
        if (recordMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("该分类已有流水记录，无法删除");
        }
        
        categoryMapper.deleteById(categoryId);
        log.info("删除分类成功: ID={}", categoryId);
    }

    // ==================== 财务报表 ====================

    @Override
    public BigDecimal calculateTotalIncome(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getType, FinanceType.INCOME)
               .eq(FinanceRecord::getStatus, RecordStatus.COMPLETED);
        
        if (startTime != null) {
            wrapper.ge(FinanceRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(FinanceRecord::getCreateTime, endTime);
        }
        
        List<FinanceRecord> records = recordMapper.selectList(wrapper);
        return records.stream()
                .map(FinanceRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal calculateTotalExpense(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getType, FinanceType.EXPENSE)
               .eq(FinanceRecord::getStatus, RecordStatus.COMPLETED);
        
        if (startTime != null) {
            wrapper.ge(FinanceRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(FinanceRecord::getCreateTime, endTime);
        }
        
        List<FinanceRecord> records = recordMapper.selectList(wrapper);
        return records.stream()
                .map(FinanceRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal calculateProfit(LocalDateTime startTime, LocalDateTime endTime) {
        BigDecimal income = calculateTotalIncome(startTime, endTime);
        BigDecimal expense = calculateTotalExpense(startTime, endTime);
        return income.subtract(expense);
    }

    @Override
    public List<CategoryStatistics> calculateCategoryStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getStatus, RecordStatus.COMPLETED);
        
        if (startTime != null) {
            wrapper.ge(FinanceRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(FinanceRecord::getCreateTime, endTime);
        }
        
        List<FinanceRecord> records = recordMapper.selectList(wrapper);
        
        // 按类别分组统计
        Map<Long, List<FinanceRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(FinanceRecord::getCategoryId));
        
        return grouped.entrySet().stream()
                .map(entry -> {
                    CategoryStatistics stats = new CategoryStatistics();
                    stats.setCategoryId(entry.getKey());
                    
                    FinanceCategory category = categoryMapper.selectById(entry.getKey());
                    stats.setCategoryName(category != null ? category.getName() : "未知");
                    
                    BigDecimal income = entry.getValue().stream()
                            .filter(r -> r.getType() == FinanceType.INCOME)
                            .map(FinanceRecord::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    BigDecimal expense = entry.getValue().stream()
                            .filter(r -> r.getType() == FinanceType.EXPENSE)
                            .map(FinanceRecord::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    stats.setIncome(income);
                    stats.setExpense(expense);
                    stats.setNetAmount(income.subtract(expense));
                    stats.setRecordCount(entry.getValue().size());
                    
                    return stats;
                })
                .sorted((a, b) -> b.getNetAmount().compareTo(a.getNetAmount()))
                .collect(Collectors.toList());
    }

    // ==================== 统计分析 ====================

    @Override
    public FinanceDailyStatistics getDailyStatistics(LocalDateTime date) {
        LocalDateTime startTime = date.withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endTime = date.withHour(23).withMinute(59).withSecond(59);
        
        FinanceDailyStatistics statistics = new FinanceDailyStatistics();
        statistics.setDate(date);
        statistics.setIncome(calculateTotalIncome(startTime, endTime));
        statistics.setExpense(calculateTotalExpense(startTime, endTime));
        statistics.setProfit(statistics.getIncome().subtract(statistics.getExpense()));
        
        // 统计交易笔数
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(FinanceRecord::getCreateTime, startTime, endTime)
               .eq(FinanceRecord::getStatus, RecordStatus.COMPLETED);
        statistics.setRecordCount((int) recordMapper.selectCount(wrapper));
        
        return statistics;
    }

    @Override
    public List<FinanceDailyStatistics> getDailyStatisticsList(LocalDate startDate, LocalDate endDate) {
        List<FinanceDailyStatistics> result = new ArrayList<>();
        
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            result.add(getDailyStatistics(current.atStartOfDay()));
            current = current.plusDays(1);
        }
        
        return result;
    }

    @Override
    public List<FinanceMonthlyStatistics> getMonthlyStatistics(Integer year, Integer month) {
        LocalDateTime startTime = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime endTime = startTime.plusMonths(1).minusSeconds(1);
        
        // 按天统计
        List<FinanceDailyStatistics> dailyStats = getDailyStatisticsList(
                startTime.toLocalDate(), endTime.toLocalDate());
        
        // 汇总月度统计
        FinanceMonthlyStatistics monthlyStats = new FinanceMonthlyStatistics();
        monthlyStats.setYear(year);
        monthlyStats.setMonth(month);
        monthlyStats.setTotalIncome(dailyStats.stream()
                .map(FinanceDailyStatistics::getIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        monthlyStats.setTotalExpense(dailyStats.stream()
                .map(FinanceDailyStatistics::getExpense)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        monthlyStats.setTotalProfit(monthlyStats.getTotalIncome().subtract(monthlyStats.getTotalExpense()));
        monthlyStats.setTotalRecordCount(dailyStats.stream()
                .mapToInt(FinanceDailyStatistics::getRecordCount).sum());
        
        // 平均日收入/支出
        if (!dailyStats.isEmpty()) {
            monthlyStats.setAvgDailyIncome(monthlyStats.getTotalIncome()
                    .divide(BigDecimal.valueOf(dailyStats.size()), 2, RoundingMode.HALF_UP));
            monthlyStats.setAvgDailyExpense(monthlyStats.getTotalExpense()
                    .divide(BigDecimal.valueOf(dailyStats.size()), 2, RoundingMode.HALF_UP));
        }
        
        // 按类别统计
        List<CategoryStatistics> categoryStats = calculateCategoryStatistics(startTime, endTime);
        monthlyStats.setCategoryStatistics(categoryStats);
        
        return Arrays.asList(monthlyStats);
    }

    @Override
    public FinanceOverview getOverview(Long accountId, LocalDateTime startTime, LocalDateTime endTime) {
        FinanceOverview overview = new FinanceOverview();
        
        // 收入支出
        BigDecimal totalIncome = accountId != null ? 
                calculateAccountIncome(accountId, startTime, endTime) :
                calculateTotalIncome(startTime, endTime);
        BigDecimal totalExpense = accountId != null ?
                calculateAccountExpense(accountId, startTime, endTime) :
                calculateTotalExpense(startTime, endTime);
        
        overview.setTotalIncome(totalIncome);
        overview.setTotalExpense(totalExpense);
        overview.setProfit(totalIncome.subtract(totalExpense));
        
        // 利润率
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitRate = totalIncome.subtract(totalExpense)
                    .divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            overview.setProfitRate(profitRate);
        }
        
        // 账户余额
        if (accountId != null) {
            FinanceAccount account = accountMapper.selectById(accountId);
            overview.setAccountBalance(account != null ? account.getBalance() : BigDecimal.ZERO);
        } else {
            List<FinanceAccount> accounts = accountMapper.selectList(null);
            BigDecimal totalBalance = accounts.stream()
                    .map(FinanceAccount::getBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            overview.setAccountBalance(totalBalance);
        }
        
        // 交易笔数
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getStatus, RecordStatus.COMPLETED);
        if (accountId != null) {
            wrapper.eq(FinanceRecord::getAccountId, accountId);
        }
        if (startTime != null) {
            wrapper.ge(FinanceRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(FinanceRecord::getCreateTime, endTime);
        }
        overview.setRecordCount((int) recordMapper.selectCount(wrapper));
        
        return overview;
    }

    @Override
    public List<AccountStatistics> getAccountStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        List<FinanceAccount> accounts = accountMapper.selectList(null);
        
        return accounts.stream().map(account -> {
            AccountStatistics stats = new AccountStatistics();
            stats.setAccountId(account.getId());
            stats.setAccountName(account.getName());
            stats.setCurrentBalance(account.getBalance());
            
            stats.setIncome(calculateAccountIncome(account.getId(), startTime, endTime));
            stats.setExpense(calculateAccountExpense(account.getId(), startTime, endTime));
            stats.setNetAmount(stats.getIncome().subtract(stats.getExpense()));
            
            // 交易笔数
            LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FinanceRecord::getAccountId, account.getId())
                   .eq(FinanceRecord::getStatus, RecordStatus.COMPLETED);
            if (startTime != null) wrapper.ge(FinanceRecord::getCreateTime, startTime);
            if (endTime != null) wrapper.le(FinanceRecord::getCreateTime, endTime);
            stats.setRecordCount((int) recordMapper.selectCount(wrapper));
            
            return stats;
        }).collect(Collectors.toList());
    }

    @Override
    public FinanceTrend getTrend(LocalDate startDate, LocalDate endDate) {
        FinanceTrend trend = new FinanceTrend();
        
        List<FinanceDailyStatistics> dailyStats = getDailyStatisticsList(startDate, endDate);
        
        // 转换为图表数据
        List<String> dates = new ArrayList<>();
        List<BigDecimal> incomes = new ArrayList<>();
        List<BigDecimal> expenses = new ArrayList<>();
        List<BigDecimal> profits = new ArrayList<>();
        
        for (FinanceDailyStatistics stat : dailyStats) {
            dates.add(stat.getDate().format(DateTimeFormatter.ofPattern("MM-DD")));
            incomes.add(stat.getIncome());
            expenses.add(stat.getExpense());
            profits.add(stat.getProfit());
        }
        
        trend.setDates(dates);
        trend.setIncomes(incomes);
        trend.setExpenses(expenses);
        trend.setProfits(profits);
        
        // 计算趋势
        if (dailyStats.size() >= 2) {
            FinanceDailyStatistics latest = dailyStats.get(dailyStats.size() - 1);
            FinanceDailyStatistics previous = dailyStats.get(dailyStats.size() - 2);
            
            // 收入变化
            if (previous.getIncome().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal incomeChange = latest.getIncome()
                        .subtract(previous.getIncome())
                        .divide(previous.getIncome(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                trend.setIncomeChangeRate(incomeChange);
            }
            
            // 支出变化
            if (previous.getExpense().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal expenseChange = latest.getExpense()
                        .subtract(previous.getExpense())
                        .divide(previous.getExpense(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                trend.setExpenseChangeRate(expenseChange);
            }
        }
        
        return trend;
    }

    // ==================== 对账功能 ====================

    @Override
    public ReconciliationResult reconcile(Long accountId, LocalDateTime startTime, LocalDateTime endTime) {
        ReconciliationResult result = new ReconciliationResult();
        
        // 获取所有流水记录
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getAccountId, accountId)
               .eq(FinanceRecord::getStatus, RecordStatus.COMPLETED)
               .between(FinanceRecord::getCreateTime, startTime, endTime)
               .orderByAsc(FinanceRecord::getCreateTime);
        
        List<FinanceRecord> records = recordMapper.selectList(wrapper);
        
        // 检查余额是否连续
        BigDecimal expectedBalance = accountMapper.selectById(accountId).getBalance();
        BigDecimal calculatedBalance = BigDecimal.ZERO;
        
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < records.size(); i++) {
            FinanceRecord record = records.get(i);
            calculatedBalance = calculatedBalance.add(
                    record.getType() == FinanceType.INCOME ? record.getAmount() : record.getAmount().negate());
            
            // 检查每条记录的余额是否连续
            if (i < records.size() - 1) {
                FinanceRecord nextRecord = records.get(i + 1);
                if (!record.getAfterBalance().equals(nextRecord.getBeforeBalance())) {
                    errors.add(String.format("记录[%s]余额不连续: 记录余额=%s, 下一条记录期初=%s",
                            record.getRecordNo(), record.getAfterBalance(), nextRecord.getBeforeBalance()));
                }
            }
        }
        
        // 检查计算余额与账户余额是否一致
        if (expectedBalance.compareTo(calculatedBalance) != 0) {
            errors.add(String.format("账户余额与流水计算不一致: 账户余额=%s, 计算余额=%s",
                    expectedBalance, calculatedBalance));
        }
        
        result.setAccountId(accountId);
        result.setRecordCount(records.size());
        result.setExpectedBalance(expectedBalance);
        result.setCalculatedBalance(calculatedBalance);
        result.setErrors(errors);
        result.setBalanced(errors.isEmpty());
        result.setReconcileTime(LocalDateTime.now());
        
        log.info("对账完成: 账户ID={}, 记录数={}, 是否平衡={}", accountId, records.size(), errors.isEmpty());
        return result;
    }

    // ==================== 批量操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchFinanceResult batchRecordFinance(List<FinanceRecord> records) {
        BatchFinanceResult result = new BatchFinanceResult();
        List<Long> successIds = new ArrayList<>();
        List<String> failedReasons = new ArrayList<>();
        
        for (FinanceRecord record : records) {
            try {
                Long id = recordFinance(record);
                successIds.add(id);
            } catch (Exception e) {
                failedReasons.add(String.format("记录[%s]: %s", record.getRecordNo(), e.getMessage()));
            }
        }
        
        result.setSuccessCount(successIds.size());
        result.setFailedCount(failedReasons.size());
        result.setSuccessIds(successIds);
        result.setFailedReasons(failedReasons);
        
        log.info("批量记录财务完成: 成功={}, 失败={}", successIds.size(), failedReasons.size());
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 计算指定账户的收入
     */
    private BigDecimal calculateAccountIncome(Long accountId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getAccountId, accountId)
               .eq(FinanceRecord::getType, FinanceType.INCOME)
               .eq(FinanceRecord::getStatus, RecordStatus.COMPLETED);
        
        if (startTime != null) wrapper.ge(FinanceRecord::getCreateTime, startTime);
        if (endTime != null) wrapper.le(FinanceRecord::getCreateTime, endTime);
        
        List<FinanceRecord> records = recordMapper.selectList(wrapper);
        return records.stream().map(FinanceRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算指定账户的支出
     */
    private BigDecimal calculateAccountExpense(Long accountId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinanceRecord::getAccountId, accountId)
               .eq(FinanceRecord::getType, FinanceType.EXPENSE)
               .eq(FinanceRecord::getStatus, RecordStatus.COMPLETED);
        
        if (startTime != null) wrapper.ge(FinanceRecord::getCreateTime, startTime);
        if (endTime != null) wrapper.le(FinanceRecord::getCreateTime, endTime);
        
        List<FinanceRecord> records = recordMapper.selectList(wrapper);
        return records.stream().map(FinanceRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 更新账户统计
     */
    private void updateAccountStatistics(Long accountId, FinanceRecord record) {
        // 这里可以更新缓存或统计数据
        // 实际实现可以根据需求添加
    }

    /**
     * 生成财务记录号
     */
    private String generateRecordNo() {
        return "FIN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) 
                + String.format("%04d", new Random().nextInt(10000));
    }

    /**
     * 生成账户号
     */
    private String generateAccountNo() {
        return "ACC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) 
                + String.format("%04d", new Random().nextInt(10000));
    }
}