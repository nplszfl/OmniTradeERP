package com.crossborder.erp.platform.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.common.exception.PlatformApiException;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.annotation.ApiMonitor;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import com.crossborder.erp.platform.config.AmazonConfig;
import com.crossborder.erp.platform.util.AmazonSPApiSigner;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 亚马逊订单同步实现（完整版）
 * 使用亚马逊Selling Partner API (SP-API)
 */
@Slf4j
@Component
public class AmazonOrderSync implements PlatformOrderSync {

    private final AmazonConfig amazonConfig;
    private final AmazonSPApiSigner signer;

    @Autowired
    public AmazonOrderSync(AmazonConfig amazonConfig, AmazonSPApiSigner signer) {
        this.amazonConfig = amazonConfig;
        this.signer = signer;
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.AMAZON;
    }

    @Override
    @ApiMonitor(platform = "amazon")
    public List<Order> syncOrders(LocalDateTime startTime, LocalDateTime endTime, String shopId) {
        log.info("开始同步亚马逊订单, 店铺: {}, 时间范围: {} - {}", shopId, startTime, endTime);

        List<Order> orders = new ArrayList<>();

        try {
            // 构建API请求参数
            String uri = buildGetOrdersUri(startTime, endTime);
            String body = buildGetOrdersBody();

            // 签名请求
            Map<String, String> headers = signer.signRequest(uri, "POST", body);

            // 发送HTTP请求
            String response = sendHttpRequest(uri, "POST", headers, body);

            // 解析响应
            orders = parseOrdersResponse(response);

            log.info("亚马逊订单同步完成, 共获取 {} 条订单", orders.size());
            return orders;

        } catch (Exception e) {
            // 🔴 原来这里 `return orders`（空列表）→ **API 调用失败和「真的没有订单」
            //    完全无法区分**。定时任务会记成「同步成功，count=0」，
            //    凭据失效 / 平台限流 / 签名错误全部被吞掉，没有任何告警。
            //    📌 教训：空集合不是「无数据」的语义，是「我不知道」的语义。
            log.error("同步亚马逊订单失败: shopId={}", shopId, e);
            throw PlatformApiException.amazon("SYNC_ORDERS_FAILED",
                    "同步亚马逊订单失败: " + e.getMessage(), e);
        }
    }

    @Override
    @ApiMonitor(platform = "amazon")
    public Order getOrderByNo(String platformOrderNo, String shopId) {
        log.info("获取亚马逊订单详情, 订单号: {}", platformOrderNo);

        try {
            // 构建获取订单详情请求
            String uri = "/orders/v0/orders/" + platformOrderNo;
            String body = "{}";

            // 签名请求
            Map<String, String> headers = signer.signRequest(uri, "GET", body);

            // 发送HTTP请求
            String response = sendHttpRequest(uri, "GET", headers, body);

            // 解析响应
            Order order = parseOrderResponse(response);

            // 同步订单商品明细
            List<OrderItem> items = syncOrderItems(platformOrderNo, shopId);

            log.info("获取亚马逊订单详情成功, 订单号: {}", platformOrderNo);
            return order;

        } catch (Exception e) {
            log.error("获取亚马逊订单详情失败: orderNo={}", platformOrderNo, e);
            throw PlatformApiException.amazon("GET_ORDER_FAILED",
                    "获取亚马逊订单详情失败: " + e.getMessage(), e);
        }
    }

    @Override
    @ApiMonitor(platform = "amazon")
    public List<OrderItem> syncOrderItems(String platformOrderNo, String shopId) {
        log.info("同步亚马逊订单商品明细, 订单号: {}", platformOrderNo);

        List<OrderItem> items = new ArrayList<>();

        try {
            // 构建获取订单商品请求
            String uri = buildGetOrderItemsUri(platformOrderNo);
            String body = "{}";

            // 签名请求
            Map<String, String> headers = signer.signRequest(uri, "GET", body);

            // 发送HTTP请求
            String response = sendHttpRequest(uri, "GET", headers, body);

            // 解析响应
            items = parseOrderItemsResponse(response);

            log.info("同步亚马逊订单商品明细完成, 订单号: {}, 商品数量: {}", platformOrderNo, items.size());
            return items;

        } catch (Exception e) {
            log.error("同步亚马逊订单商品明细失败: orderNo={}", platformOrderNo, e);
            throw PlatformApiException.amazon("SYNC_ORDER_ITEMS_FAILED",
                    "同步亚马逊订单商品明细失败: " + e.getMessage(), e);
        }
    }

    @Override
    @ApiMonitor(platform = "amazon")
    public boolean validateConfig(String shopId) {
        log.info("验证亚马逊API配置, 店铺: {}", shopId);

        try {
            // 调用GetMarketplaceParticipations接口验证
            String uri = "/sellers/v1/marketplaceParticipations";
            String body = "{}";

            Map<String, String> headers = signer.signRequest(uri, "GET", body);
            String response = sendHttpRequest(uri, "GET", headers, body);

            // 检查响应
            JSONObject json = JSON.parseObject(response);
            JSONArray participations = json.getJSONArray("payload");

            return participations != null && participations.size() > 0;

        } catch (Exception e) {
            // 📌 validateConfig 返回 false 是**有意的语义**（连接测试就是问「通不通」），
            //    不属于静默失败。但必须记下原因，否则页面只显示「测试失败」。
            log.error("验证亚马逊API配置失败: shopId={}", shopId, e);
            return false;
        }
    }

    /**
     * 构建获取订单列表的URI
     */
    private String buildGetOrdersUri(LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder uri = new StringBuilder("/orders/v0/orders");
        uri.append("?MarketPlaceIds=").append(amazonConfig.getMarketplaceId());
        uri.append("&CreatedAfter=").append(startTime.format(DATE_FORMATTER));
        uri.append("&CreatedBefore=").append(endTime.format(DATE_FORMATTER));
        uri.append("&OrderStatuses=Unshipped,PartiallyShipped,Shipped");
        uri.append("&MaxResultsPerPage=100");
        return uri.toString();
    }

    /**
     * 构建获取订单列表的请求体
     */
    private String buildGetOrdersBody() {
        // 创建日期在指定时间范围内
        JSONObject body = new JSONObject();

        // 可以添加更多的查询参数
        return body.toJSONString();
    }

    /**
     * 构建获取订单商品的URI
     */
    private String buildGetOrderItemsUri(String amazonOrderId) {
        return "/orders/v0/orders/" + amazonOrderId + "/orderItems";
    }

    /**
     * 发送HTTP请求
     */
    private String sendHttpRequest(String uri, String method, Map<String, String> headers, String body) throws Exception {
        String url = amazonConfig.getApiBaseUrl() + uri;

        Request.Builder builder = new Request.Builder()
                .url(url);

        if ("GET".equalsIgnoreCase(method)) {
            builder.get();
        } else if ("POST".equalsIgnoreCase(method)) {
            builder.post(RequestBody.create(body, MediaType.parse("application/json")));
        }

        // 添加请求头
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }

        Request request = builder.build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP请求失败: " + response.code());
            }
            return response.body().string();
        }
    }

    /**
     * 解析订单列表响应
     */
    private List<Order> parseOrdersResponse(String response) {
        List<Order> orders = new ArrayList<>();

        JSONObject json = JSON.parseObject(response);
        JSONArray orderList = json.getJSONArray("payload");

        if (orderList != null) {
            for (int i = 0; i < orderList.size(); i++) {
                JSONObject orderJson = orderList.getJSONObject(i);
                Order order = convertToOrder(orderJson);
                orders.add(order);
            }
        }

        return orders;
    }

    /**
     * 解析单个订单响应
     */
    private Order parseOrderResponse(String response) {
        JSONObject json = JSON.parseObject(response);
        JSONObject orderJson = json.getJSONObject("payload");
        return convertToOrder(orderJson);
    }

    /**
     * 将亚马逊订单转换为内部Order对象
     */
    private Order convertToOrder(JSONObject amazonOrder) {
        Order order = new Order();

        // 基本信息
        order.setPlatform("amazon");
        order.setShopId(amazonConfig.getMerchantId());
        order.setPlatformOrderNo(amazonOrder.getString("AmazonOrderId"));
        order.setInternalOrderNo(generateInternalOrderNo());

        // 买家信息
        JSONObject buyerInfo = amazonOrder.getJSONObject("BuyerInfo");
        if (buyerInfo != null) {
            order.setBuyerId(buyerInfo.getString("BuyerId"));
            order.setBuyerEmail(buyerInfo.getString("BuyerEmail"));
        }

        // 金额信息
        JSONObject orderTotal = amazonOrder.getJSONObject("OrderTotal");
        if (orderTotal != null) {
            order.setOrderAmount(new BigDecimal(orderTotal.getString("Amount")));
            // 🔴 币种必须设：t_order.currency_code 是 NOT NULL 且**无默认值**，
            //    MyBatis-Plus 的 insert 默认 FieldStrategy.NOT_NULL 会**跳过 null 字段**，
            //    于是 INSERT 里根本没有 currency_code 这一列 →
            //    MySQL 报 "Field 'currency_code' doesn't have a default value" → 整个订单存不进去。
            //    📌 之前从没设过 → 平台订单同步**一单都存不进来**，
            //    异常又被 catch 吞成一行日志，看起来像「同步跑过了」。
            order.setCurrencyCode(orderTotal.getString("Currency"));
        }

        // 订单状态
        String amazonStatus = amazonOrder.getString("OrderStatus");
        order.setStatus(convertAmazonStatus(amazonStatus));

        // 支付状态
        String paymentStatus = amazonOrder.getString("PaymentStatus");
        order.setPaymentStatus(convertPaymentStatus(paymentStatus));

        // 收货地址
        JSONObject shippingAddress = amazonOrder.getJSONObject("ShippingAddress");
        if (shippingAddress != null) {
            order.setRecipientName(shippingAddress.getString("Name"));
            order.setRecipientAddress(shippingAddress.getString("AddressLine1"));
            order.setRecipientCity(shippingAddress.getString("City"));
            order.setRecipientState(shippingAddress.getString("StateOrRegion"));
            order.setRecipientPostalCode(shippingAddress.getString("PostalCode"));
            order.setRecipientCountry(shippingAddress.getString("CountryCode"));
            order.setRecipientPhone(shippingAddress.getString("Phone"));
        }

        // 物流信息
        JSONObject fulfillmentData = amazonOrder.getJSONObject("FulfillmentData");
        if (fulfillmentData != null) {
            order.setLogisticsCompany(fulfillmentData.getString("FulfillmentChannel"));
            order.setTrackingNumber(fulfillmentData.getString("TrackingNumber"));
        }

        // 保存原始数据
        order.setRawData(amazonOrder.toJSONString());

        return order;
    }

    /**
     * 解析订单商品列表响应
     */
    private List<OrderItem> parseOrderItemsResponse(String response) {
        List<OrderItem> items = new ArrayList<>();

        JSONObject json = JSON.parseObject(response);
        JSONArray itemList = json.getJSONArray("payload");

        if (itemList != null) {
            for (int i = 0; i < itemList.size(); i++) {
                JSONObject itemJson = itemList.getJSONObject(i);
                OrderItem item = convertToOrderItem(itemJson);
                items.add(item);
            }
        }

        return items;
    }

    /**
     * 将亚马逊订单商品转换为内部OrderItem对象
     */
    private OrderItem convertToOrderItem(JSONObject amazonItem) {
        OrderItem item = new OrderItem();

        item.setPlatformProductId(amazonItem.getString("ASIN"));
        item.setPlatformSku(amazonItem.getString("SellerSKU"));
        item.setProductName(amazonItem.getString("Title"));
        
        // 商品图片
        JSONObject productInfo = amazonItem.getJSONObject("ProductInfo");
        if (productInfo != null) {
            JSONObject mainImage = productInfo.getJSONObject("MainImage");
            if (mainImage != null) {
                item.setProductImage(mainImage.getString("URL"));
            }
        }
        
        // 单价
        JSONObject unitPrice = amazonItem.getJSONObject("UnitPrice");
        if (unitPrice != null) {
            item.setUnitPrice(new BigDecimal(unitPrice.getString("Amount")));
            // 📌 t_order_item.currency_code 同样 NOT NULL 且无默认值
            item.setCurrencyCode(unitPrice.getString("Currency"));
        }
        
        item.setQuantity(amazonItem.getInteger("QuantityOrdered"));

        // 计算总金额
        if (item.getUnitPrice() != null && item.getQuantity() != null) {
            item.setTotalAmount(item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())));
        }

        return item;
    }

    /**
     * 转换亚马逊订单状态
     */
    private String convertAmazonStatus(String amazonStatus) {
        switch (amazonStatus) {
            case "PendingAvailability":
            case "Pending":
                return "pending_payment";
            case "Unshipped":
            case "PartiallyShipped":
                return "pending_shipment";
            case "Shipped":
                return "shipped";
            case "Delivered":
                return "delivered";
            case "Canceled":
                return "cancelled";
            case "Refunded":
                return "refunded";
            default:
                return "unknown";
        }
    }

    /**
     * 转换支付状态
     */
    private String convertPaymentStatus(String amazonPaymentStatus) {
        switch (amazonPaymentStatus) {
            case "NotSettled":
                return "pending";
            case "Settled":
                return "success";
            case "Rejected":
                return "failed";
            default:
                return "unknown";
        }
    }

    /**
     * 生成内部订单号
     */
    private String generateInternalOrderNo() {
        return "AMZ" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
