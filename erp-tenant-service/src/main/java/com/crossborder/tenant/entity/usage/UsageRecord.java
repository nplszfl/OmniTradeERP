package com.crossborder.tenant.entity.usage;

import jakarta.persistence.*;
import com.crossborder.tenant.converter.YearMonthConverter;
import lombok.*;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * 租户使用量记录
 */
@Entity
@Table(name = "tenant_usage_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 租户ID
     */
    @Column(nullable = false)
    private String tenantId;

    /**
     * 统计月份
     *
     * ⚠️ 必须显式指定 YearMonth → VARCHAR 转换器：Hibernate 6.2.x **不支持 YearMonth**，
     *    会当成 Serializable 处理 → 有数据时读取直接抛
     *    {@code SerializationException: could not deserialize}（接口 500）
     */
    @Convert(converter = YearMonthConverter.class)
    @Column(nullable = false)
    private YearMonth billingPeriod;

    /**
     * API调用次数
     */
    @Builder.Default
    private Long apiCalls = 0L;

    /**
     * 存储用量（字节）
     */
    @Builder.Default
    private Long storageBytes = 0L;

    /**
     * 订单数量
     */
    @Builder.Default
    private Integer orderCount = 0;

    /**
     * 平台账号数量
     */
    @Builder.Default
    private Integer platformCount = 0;

    /**
     * AI助手调用次数
     */
    @Builder.Default
    private Long aiCalls = 0L;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}