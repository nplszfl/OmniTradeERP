package com.crossborder.tenant.repository.usage;

import com.crossborder.tenant.entity.usage.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 使用量记录仓库
 */
@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, String> {

    /**
     * 查询指定租户指定月份的使用量
     */
    Optional<UsageRecord> findByTenantIdAndBillingPeriod(String tenantId, YearMonth billingPeriod);

    /**
     * 查询指定租户最近的使用量
     */
    Optional<UsageRecord> findTopByTenantIdOrderByBillingPeriodDesc(String tenantId);

    /**
     * 查询指定租户最近 N 个计费周期的用量（按周期倒序）
     *
     * <p>📌 趋势接口用这个查真实数据 —— 之前的实现直接用
     * {@code Math.random()} 造数（代码注释写着「模拟数据，实际应从日志表查询」），
     * 页面上的「用量趋势」图每次刷新都不一样。</p>
     */
    List<UsageRecord> findTop12ByTenantIdOrderByBillingPeriodDesc(String tenantId);
}