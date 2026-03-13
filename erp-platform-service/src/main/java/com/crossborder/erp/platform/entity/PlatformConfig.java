package com.crossborder.erp.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台配置实体
 * 存储各平台的API密钥、回调地址等配置信息
 */
@Data
@TableName("t_platform_config")
public class PlatformConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 平台类型（amazon/ebay/shopee等）
     */
    private String platform;

    /**
     * 店铺ID/店铺代码
     */
    @TableField("shop_id")
    private String shopId;

    /**
     * 店铺名称
     */
    @TableField("shop_name")
    private String shopName;

    /**
     * API Key
     */
    @TableField("api_key")
    private String apiKey;

    /**
     * API Secret
     */
    @TableField("api_secret")
    private String apiSecret;

    /**
     * Access Token（OAuth类平台使用）
     */
    @TableField("access_token")
    private String accessToken;

    /**
     * Refresh Token
     */
    @TableField("refresh_token")
    private String refreshToken;

    /**
     * Token过期时间
     */
    @TableField("token_expire_time")
    private LocalDateTime tokenExpireTime;

    /**
     * API基础URL
     */
    @TableField("api_base_url")
    private String apiBaseUrl;

    /**
     * 回调URL
     */
    @TableField("callback_url")
    private String callbackUrl;

    /**
     * 店铺状态（0停用 1启用）
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;
}
