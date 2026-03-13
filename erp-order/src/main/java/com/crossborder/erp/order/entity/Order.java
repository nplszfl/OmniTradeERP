package com.crossborder.erp.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 * 支持日处理10万+订单量
 */
@Data
@TableName("t_order")
public class Order {

    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 平台类型（amazon/ebay/shopee等）
     */
    private String platform;

    /**
     * 平台店铺ID
     */
    private String shopId;

    /**
     * 平台订单号（原始平台订单ID）
     */
    @TableField("platform_order_no")
    private String platformOrderNo;

    /**
     * 内部订单号（系统生成的唯一标识）
     */
    @TableField("internal_order_no")
    private String internalOrderNo;

    /**
     * 买家ID（平台用户标识）
     */
    @TableField("buyer_id")
    private String buyerId;

    /**
     * 买家姓名
     */
    @TableField("buyer_name")
    private String buyerName;

    /**
     * 买家邮箱
     */
    @TableField("buyer_email")
    private String buyerEmail;

    /**
     * 买家手机号
     @TableField("buyer_phone")
     private String buyerPhone;

    /**
     * 订单金额（订单总金额）
     */
    @TableField("order_amount")
    private BigDecimal orderAmount;

    /**
     * 商品金额（商品总额）
     */
    @TableField("product_amount")
    private BigDecimal productAmount;

    /**
     * 运费金额
     */
    @TableField("shipping_amount")
    private BigDecimal shippingAmount;

    /**
     * 税费
     */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /**
     * 折扣金额
     */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    /**
     * 货币代码（USD/EUR/GBP等）
     */
    @TableField("currency_code")
    private String currencyCode;

    /**
     * 订单状态（pending_payment/pending_shipment/shipped等）
     */
    private String status;

    /**
     * 支付状态
     */
    @TableField("payment_status")
    private String paymentStatus;

    /**
     * 支付方式
     */
    @TableField("payment_method")
    private String paymentMethod;

    /**
     * 支付时间
     */
    @TableField("payment_time")
    private LocalDateTime paymentTime;

    /**
     * 发货方式
     */
    @TableField("shipping_method")
    private String shippingMethod;

    /**
     * 物流公司
     */
    @TableField("logistics_company")
    private String logisticsCompany;

    /**
     * 物流单号
     */
    @TableField("tracking_number")
    private String trackingNumber;

    /**
     * 发货时间
     */
    @TableField("shipping_time")
    private LocalDateTime shippingTime;

    /**
     * 收货人姓名
     */
    @TableField("recipient_name")
    private String recipientName;

    /**
     * 收货人国家
     */
    @TableField("recipient_country")
    private String recipientCountry;

    /**
     * 收货人省份/州
     */
    @TableField("recipient_state")
    private String recipientState;

    /**
     * 收货人城市
     */
    @TableField("recipient_city")
    private String recipientCity;

    /**
     * 收货人详细地址
     */
    @TableField("recipient_address")
    private String recipientAddress;

    /**
     * 收货人邮编
     */
    @TableField("recipient_postal_code")
    private String recipientPostalCode;

    /**
     * 收货人电话
     */
    @TableField("recipient_phone")
    private String recipientPhone;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 平台原始数据（JSON格式，保存完整的平台订单信息）
     */
    @TableField("raw_data")
    private String rawData;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableTable(fill = FieldFill.INSERT_UPDATE)
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
     * 逻辑删除标记（0正常 1删除）
     */
    @TableLogic
    private Integer deleted;
}
