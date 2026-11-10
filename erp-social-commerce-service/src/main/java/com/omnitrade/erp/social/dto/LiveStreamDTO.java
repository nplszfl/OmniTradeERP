package com.omnitrade.erp.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveStreamDTO {
    
    private Long id;
    
    @NotBlank(message = "直播ID不能为空")
    private String streamId;
    
    @NotBlank(message = "直播标题不能为空")
    private String title;
    
    @NotNull(message = "平台不能为空")
    private String platform;

    /**
     * 📌 所属店铺 ID（2026-11-10 新增）—— 直播记录归属到哪个店铺。
     * ⚠️ 存量记录可能为 null（列是后加的）。
     */
    private String shopId;
    
    private Long influencerId;
    
    private String status;
    
    private String scheduledStartTime;
    
    private String actualStartTime;
    
    private String endTime;
    
    private Integer duration;
    
    private Integer viewerCount;
    
    private Integer peakViewers;
    
    private Integer likeCount;
    
    private Integer shareCount;
    
    private Integer commentCount;
    
    private Integer newFollowers;
    
    private BigDecimal totalSales;
    
    private Integer totalOrders;
    
    private BigDecimal totalGmv;
    
    private String coverImageUrl;
    
    private String products;
    
    private String createdAt;
    private String updatedAt;
}