package com.crossborder.erp.finance.converter;

import com.crossborder.erp.finance.dto.FinanceAccountRequest;
import com.crossborder.erp.finance.dto.FinanceCategoryRequest;
import com.crossborder.erp.finance.dto.FinanceFlowRequest;
import com.crossborder.erp.finance.entity.FinanceAccount;
import com.crossborder.erp.finance.entity.FinanceCategory;
import com.crossborder.erp.finance.entity.FinanceRecord;
import com.crossborder.erp.finance.enums.AccountStatus;
import com.crossborder.erp.finance.enums.CategoryType;
import com.crossborder.erp.finance.enums.FinanceType;
import com.crossborder.erp.finance.enums.RecordStatus;
import com.crossborder.erp.finance.vo.FinanceAccountVO;
import com.crossborder.erp.finance.vo.FinanceCategoryVO;
import com.crossborder.erp.finance.vo.FinanceFlowVO;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * 财务模块前后端字段契约转换
 *
 * <p>前端契约见 {@code erp-web/src/api/finance.ts}：
 * <ul>
 *   <li>账户名：前端 {@code accountName} ↔ 后端实体 {@code name}</li>
 *   <li>账户类型 / 流水类型 / 分类类型：前端小写（bank / income / expense / refund）</li>
 *   <li>账户状态：前端数字 1 启用 / 0 停用 ↔ 后端 {@code AccountStatus} 枚举</li>
 *   <li>分类排序：前端 {@code sort} ↔ 后端 {@code sortOrder}；分类状态：前端 {@code status} ↔ 后端 {@code enabled}</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
public final class FinanceConverter {

    private FinanceConverter() {
    }

    // ==================== 账户 ====================

    /**
     * 实体 → 账户视图
     */
    public static FinanceAccountVO toAccountVO(FinanceAccount account) {
        if (account == null) {
            return null;
        }
        FinanceAccountVO vo = new FinanceAccountVO();
        vo.setId(account.getId());
        // 前端表单里的「账号」是银行账号，对应实体 accountNumber；
        // 实体 accountNo 是系统自动生成的账户编号，另以 accountCode 暴露。
        vo.setAccountNo(account.getAccountNumber());
        vo.setAccountCode(account.getAccountNo());
        vo.setAccountName(account.getName());
        vo.setAccountType(account.getAccountType() == null
                ? null : account.getAccountType().toLowerCase(Locale.ROOT));
        vo.setBankName(account.getBankName());
        vo.setCurrency(account.getCurrency());
        vo.setBalance(account.getBalance());
        vo.setStatus(toStatusCode(account.getStatus()));
        vo.setIsDefault(account.getIsDefault() == null ? 0 : account.getIsDefault());
        vo.setCreateTime(account.getCreateTime());
        return vo;
    }

    /**
     * 请求 → 实体（创建）
     */
    public static FinanceAccount toAccountEntity(FinanceAccountRequest request) {
        FinanceAccount account = new FinanceAccount();
        account.setName(request.getAccountName());
        // 前端「账号」→ 实体 accountNumber（账号）；实体 accountNo（账户编号）由服务端生成
        account.setAccountNumber(request.getAccountNo());
        account.setAccountType(toAccountType(request.getAccountType()));
        account.setBankName(request.getBankName());
        account.setCurrency(request.getCurrency());
        account.setBalance(request.getBalance() == null ? BigDecimal.ZERO : request.getBalance());
        account.setStatus(fromStatusCode(request.getStatus()));
        account.setIsDefault(request.getIsDefault() == null ? 0 : request.getIsDefault());
        account.setRemark(request.getRemark());
        return account;
    }

    // ==================== 流水 ====================

    /**
     * 实体 → 流水视图
     *
     * @param record      流水实体
     * @param accountName 账户名称（关联带出）
     * @param categoryName 分类名称（关联带出）
     */
    public static FinanceFlowVO toFlowVO(FinanceRecord record, String accountName, String categoryName) {
        if (record == null) {
            return null;
        }
        FinanceFlowVO vo = new FinanceFlowVO();
        vo.setId(record.getId());
        vo.setRecordNo(record.getRecordNo());
        vo.setType(record.getType() == null ? null : record.getType().name().toLowerCase(Locale.ROOT));
        vo.setAmount(record.getAmount());
        vo.setAccountId(record.getAccountId());
        vo.setAccountName(accountName);
        vo.setCategoryId(record.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setOrderNo(record.getOrderNo());
        vo.setRemark(record.getRemark());
        vo.setStatus(record.getStatus() == null ? null : record.getStatus().name().toLowerCase(Locale.ROOT));
        vo.setBeforeBalance(record.getBeforeBalance());
        vo.setAfterBalance(record.getAfterBalance());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * 请求 → 实体（创建流水）
     */
    public static FinanceRecord toRecordEntity(FinanceFlowRequest request) {
        FinanceRecord record = new FinanceRecord();
        record.setType(toFinanceType(request.getType()));
        record.setAmount(request.getAmount());
        record.setAccountId(request.getAccountId());
        record.setCategoryId(request.getCategoryId());
        record.setOrderNo(request.getOrderNo());
        record.setRemark(request.getRemark());
        return record;
    }

    // ==================== 分类 ====================

    /**
     * 实体 → 分类视图
     */
    public static FinanceCategoryVO toCategoryVO(FinanceCategory category) {
        if (category == null) {
            return null;
        }
        FinanceCategoryVO vo = new FinanceCategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setType(category.getType() == null ? null : category.getType().name().toLowerCase(Locale.ROOT));
        vo.setParentId(category.getParentId());
        vo.setSort(category.getSortOrder());
        vo.setStatus(category.getEnabled() == null ? 0 : category.getEnabled());
        vo.setCreateTime(category.getCreateTime());
        return vo;
    }

    /**
     * 请求 → 实体（创建分类）
     */
    public static FinanceCategory toCategoryEntity(FinanceCategoryRequest request) {
        FinanceCategory category = new FinanceCategory();
        category.setName(request.getName());
        category.setType(toCategoryType(request.getType()));
        category.setParentId(request.getParentId());
        category.setSortOrder(request.getSort() == null ? 0 : request.getSort());
        category.setEnabled(request.getStatus() == null ? 1 : request.getStatus());
        category.setRemark(request.getRemark());
        return category;
    }

    // ==================== 枚举与基础类型 ====================

    /**
     * 账户状态 → 数字：1 启用 / 0 停用
     */
    public static Integer toStatusCode(AccountStatus status) {
        return status == AccountStatus.ACTIVE ? 1 : 0;
    }

    /**
     * 数字 → 账户状态：1 启用 / 0 停用
     */
    public static AccountStatus fromStatusCode(Integer code) {
        return code != null && code == 1 ? AccountStatus.ACTIVE : AccountStatus.INACTIVE;
    }

    /**
     * 前端小写账户类型 → 后端大写
     */
    public static String toAccountType(String type) {
        return type == null ? null : type.toUpperCase(Locale.ROOT);
    }

    /**
     * 前端小写流水类型 → 枚举
     */
    public static FinanceType toFinanceType(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        FinanceType financeType = FinanceType.getByCode(type.toUpperCase(Locale.ROOT));
        if (financeType == null) {
            throw new IllegalArgumentException("未知的流水类型: " + type);
        }
        return financeType;
    }

    /**
     * 前端小写分类类型 → 枚举
     */
    public static CategoryType toCategoryType(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        CategoryType categoryType = CategoryType.getByCode(type.toUpperCase(Locale.ROOT));
        if (categoryType == null) {
            throw new IllegalArgumentException("未知的分类类型: " + type);
        }
        return categoryType;
    }

    /**
     * 流水状态 → 小写字符串
     */
    public static String toStatusText(RecordStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }
}
