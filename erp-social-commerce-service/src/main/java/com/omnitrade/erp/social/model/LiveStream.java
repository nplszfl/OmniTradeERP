package com.omnitrade.erp.social.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "live_stream")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveStream {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String streamId;
    
    @Column(nullable = false)
    private String title;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;
    
    /**
     * 📌 **所属店铺 ID**（对应 {@code tiktok_shop_config.shop_id}，形如 {@code 'SHOP-TT-0001'}）。
     *
     * <p>⚠️ **可空是有意的**：存量数据没有这个值（列是后加的）→ 加 NOT NULL 会让
     * 历史行插不进去（规则 9：NOT NULL 无默认值列 → 插入 100% 失败）。
     * 存量行由 {@code LiveStreamMonitorService#resolveShopConfig} 走「按平台回退 + 回填」。
     *
     * <p>📌 **绝对不要用 {@code platform.name()} 当店铺 ID**：
     * {@code platform} 存的是 {@code TIKTOK/DOUYIN/TAOBAO_LIVE}，
     * 而 {@code shop_id} 存的是 {@code SHOP-TT-0001} —— 两者语义完全不同。
     * 实测（2026-11-10）：曾用 {@code findByShopId(liveStream.getPlatform().name())}
     * → 查的是 {@code shop_id='TIKTOK'} → 永远 0 行 → 每次同步都抛「店铺配置不存在」。
     */
    @Column(length = 64)
    private String shopId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_id")
    private Influencer influencer;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StreamStatus status;
    
    private LocalDateTime scheduledStartTime;
    
    private LocalDateTime actualStartTime;
    
    private LocalDateTime endTime;
    
    private Integer duration;
    
    private Integer viewerCount;
    
    private Integer peakViewers;
    
    private Integer likeCount;
    
    private Integer shareCount;
    
    private Integer commentCount;
    
    private Integer newFollowers;
    
    @Column(precision = 12, scale = 2)
    private BigDecimal totalSales;
    
    private Integer totalOrders;
    
    @Column(precision = 12, scale = 2)
    private BigDecimal totalGmv;
    
    @Column(length = 500)
    private String coverImageUrl;
    
    @Column(length = 1000)
    private String products;
    
    private LocalDateTime createdAt;
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
    
    public enum Platform {
        TIKTOK, DOUYIN, TAOBAO_LIVE
    }
    
    public enum StreamStatus {
        SCHEDULED, LIVE, ENDED, CANCELLED
    }
}