package com.crossborder.tenant.entity.billing;

import jakarta.persistence.*;
import com.crossborder.tenant.converter.YearMonthConverter;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * 账单
 */
@Entity
@Table(name = "tenant_bills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 租户ID
     */
    @Column(nullable = false)
    private String tenantId;

    /**
     * 账单周期
     *
     * ⚠️ 必须显式指定 YearMonth → VARCHAR 转换器：Hibernate 6.2.x **不支持 YearMonth**，
     *    会当成 Serializable 处理 → 有数据时读取直接抛
     *    {@code SerializationException: could not deserialize}（接口 500）
     */
    @Convert(converter = YearMonthConverter.class)
    @Column(nullable = false)
    private YearMonth billingPeriod;

    /**
     * 账单类型：SUBSCRIPTION（订阅）, USAGE（用量）, OVERDUE（逾期）
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillType billType;

    /**
     * 账单状态：PENDING（待支付）, PAID（已支付）, OVERDUE（逾期）, CANCELLED（已取消）
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillStatus status;

    /**
     * 订阅费
     */
    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal subscriptionFee = BigDecimal.ZERO;

    /**
     * 用量费用
     */
    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal usageFee = BigDecimal.ZERO;

    /**
     * 优惠金额
     */
    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    /**
     * 总金额
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * 已付金额
     */
    @Builder.Default
    @Column(precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /**
     * 支付方式
     */
    private String paymentMethod;

    /**
     * 支付时间
     */
    private LocalDateTime paidAt;

    /**
     * 到期日
     */
    private LocalDateTime dueDate;

    /**
     * 账单详情（JSON）
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (totalAmount == null) {
            totalAmount = subscriptionFee.add(usageFee).subtract(discount);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BillType {
        SUBSCRIPTION, USAGE, OVERDUE
    }

    public enum BillStatus {
        PENDING, PAID, OVERDUE, CANCELLED
    }
}