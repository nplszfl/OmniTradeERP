package com.crossborder.erp.platform.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.common.exception.PlatformApiException;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import com.crossborder.erp.platform.config.ShopeeConfig;
import com.crossborder.erp.platform.util.ShopeeApiSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import okhttp3.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Shopee订单同步实现（完整版）
 * 使用Shopee Open Platform API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopeeOrderSync implements PlatformOrderSync {

    private final ShopeeConfig shopeeConfig;
    private final ShopeeApiSigner signer;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.SHOPEE;
    }

    @Override
    public List<Order> syncOrders(LocalDateTime startTime, LocalDateTime endTime, String shopId) {
        log.info("开始同步Shopee订单, 店铺: {}, 时间范围: {} - {}", shopId, startTime, endTime);

        List<Order> orders = new ArrayList<>();

        try {
            // 1. 构建API请求参数
            String path = "/api/v2/order/get_order_list";
            Map<String, String> params = buildGetOrdersParams(startTime, endTime);

            // 2. 签名请求
            Map<String, String> signedParams = signer.signRequest(path, params);

            // 3. 发送HTTP请求
            String response = sendHttpRequest(path, signedParams);

            // 4. 解析响应
            orders = parseOrdersResponse(response);

            log.info("Shopee订单同步完成, 共获取 {} 条订单", orders.size());
            return orders;

        } catch (Exception e) {
            // 🔴 原来 return 空列表 → API 失败与「真的没有订单」无法区分
            log.error("同步Shopee订单失败: shopId={}", shopId, e);
            throw PlatformApiException.shopee("SYNC_ORDERS_FAILED",
                    "同步Shopee订单失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Order getOrderByNo(String platformOrderNo, String shopId) {
        log.info("获取Shopee订单详情, 订单号: {}", platformOrderNo);

        try {
            // 1. 构建获取订单详情请求
            String path = "/api/v2/order/get_order_detail";
            Map<String, String> params = new HashMap<>();
            params.put("order_sn", platformOrderNo);

            // 2. 签名请求
            Map<String, String> signedParams = signer.signRequest(path, params);

            // 3. 发送HTTP请求
            String response = sendHttpRequest(path, signedParams);

            // 4. 解析响应
            Order order = parseOrderResponse(response);

            log.info("获取Shopee订单详情成功, 订单号: {}", platformOrderNo);
            return order;

        } catch (Exception e) {
            log.error("获取Shopee订单详情失败: orderNo={}", platformOrderNo, e);
            throw PlatformApiException.shopee("GET_ORDER_FAILED",
                    "获取Shopee订单详情失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<OrderItem> syncOrderItems(String platformOrderNo, String shopId) {
    log.info("同步Shopee订单商品明细, 订单号: {}", platformOrderNo);

        List<OrderItem> items = new ArrayList<>();

        try {
            // 1. 构建获取订单商品请求
            String path = "/api/v2/order/get_order_detail";
            Map<String, String> params = new HashMap<>();
            params.put("order_sn", platformOrderNo);

            // 2. 签名请求
            Map<String, String> signedParams = signer.signRequest(path, params);

            // 3. 发送HTTP请求
            String response = sendHttpRequest(path, signedParams);

            // 4. 解析响应（订单商品在订单详情中）
            items = parseOrderItemsResponse(response);

            log.info("同步Shopee订单商品明细完成, 订单号: {}, 商品数量: {}", platformOrderNo, items.size());
            return items;

        } catch (Exception e) {
            log.error("同步Shopee订单商品明细失败: orderNo={}", platformOrderNo, e);
            throw PlatformApiException.shopee("SYNC_ORDER_ITEMS_FAILED",
                    "同步Shopee订单商品明细失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean validateConfig(String shopId) {
        log.info("验证Shopee API配置, 店铺: {}", shopId);

        try {
            // 调用获取店铺信息接口验证
            String path = "/api/v2/shop/get_shop_info";
            Map<String, String> params = new HashMap<>();

            Map<String, String> signedParams = signer.signRequest(path, params);
            String response = sendHttpRequest(path, signedParams);

            // 检查响应
            JSONObject json = JSON.parseObject(response);
            String error = json.getString("error");

            return error == null || "null".equals(error);

        } catch (Exception e) {
            // 📌 validateConfig 返回 false 是有意语义（连接测试问「通不通」），但要记原因
            log.error("验证Shopee API配置失败: shopId={}", shopId, e);
            return false;
        }
    }

    /**
     * 构建获取订单列表的参数
     */
    private Map<String, String> buildGetOrdersParams(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, String> params = new HashMap<>();

        // 时间范围（Unix时间戳）
        long startTimestamp = startTime.toEpochSecond(java.time.ZoneOffset.UTC);
        long endTimestamp = endTime.toEpochSecond(java.time.ZoneOffset.UTC);

        params.put("create_time_from", String.valueOf(startTimestamp));
        params.put("create_time_to", String.valueOf(endTimestamp));

        // 订单状态
        params.put("order_status", "ALL"); // ALL:所有状态, UNPAID:未付款, PAID:已付款, SHIPPED:已发货, COMPLETED:已完成, IN_CLAIM:退款中

        // 分页
        params.put("page_size", "100");
        params.put("pagination_offset", "0");

        return params;
    }

    /**
     * 发送HTTP请求
     */
    private String sendHttpRequest(String path, Map<String, String> params) throws Exception {
        String url = shopeeConfig.getApiBaseUrl() + path;

        // 构建URL参数
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
        }

        // 构建请求
        Request.Builder builder = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .addHeader("Content-Type", "application/json");

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
        JSONObject responseData = json.getJSONObject("response");

        if (responseData != null) {
            JSONArray orderList = responseData.getJSONArray("order_list");

            if (orderList != null) {
                for (int i = 0; i < orderList.size(); i++) {
                    JSONObject orderJson = orderList.getJSONObject(i);
                    Order order = convertToOrder(orderJson);
                    orders.add(order);
                }
            }
        }

        return orders;
    }

    /**
     * 解析单个订单响应
     */
    private Order parseOrderResponse(String response) {
        JSONObject json = JSON.parseObject(response);
        JSONObject responseData = json.getJSONObject("response");

        if (responseData != null) {
            return convertToOrder(responseData);
        }
        return null;
    }

    /**
     * 将Shopee订单转换为内部Order对象
     */
    private Order convertToOrder(JSONObject shopeeOrder) {
        Order order = new Order();

        // 基本信息
        order.setPlatform("shopee");
        order.setShopId(shopeeConfig.getShopId());
        order.setPlatformOrderNo(shopeeOrder.getString("order_sn"));
        order.setInternalOrderNo(generateInternalOrderNo());

        // 买家信息 - 直接从字段获取
        order.setBuyerId(shopeeOrder.getString("buyer_user_id"));
        order.setBuyerEmail(shopeeOrder.getString("recipient_email_address"));
        order.setBuyerPhone(shopeeOrder.getString("recipient_phone_number"));

        // 金额信息
        order.setOrderAmount(new BigDecimal(shopeeOrder.getString("total_amount")));
        order.setProductAmount(new BigDecimal(shopeeOrder.getString("total_amount")));

        // 订单状态
        String shopeeStatus = shopeeOrder.getString("order_status");
        order.setStatus(convertShopeeStatus(shopeeStatus));

        // 收货地址
        order.setRecipientName(shopeeOrder.getString("recipient_name"));
        order.setRecipientAddress(shopeeOrder.getString("recipient_address"));
        order.setRecipientCity(shopeeOrder.getString("recipient_city"));
        order.setRecipientState(shopeeOrder.getString("recipient_state"));
        order.setRecipientPostalCode(shopeeOrder.getString("recipient_zipcode"));
        order.setRecipientCountry(shopeeOrder.getString("recipient_region"));
        order.setRecipientPhone(shopeeOrder.getString("recipient_phone_number"));

        // 物流信息
        order.setLogisticsCompany(shopeeOrder.getString("shipping_carrier"));
        order.setTrackingNumber(shopeeOrder.getString("tracking_number"));

        // 保存原始数据
        order.setRawData(shopeeOrder.toJSONString());

        return order;
    }

    /**
     * 解析订单商品列表响应
     */
    private List<OrderItem> parseOrderItemsResponse(String response) {
        List<OrderItem> items = new ArrayList<>();

        JSONObject json = JSON.parseObject(response);
        JSONObject responseData = json.getJSONObject("response");

        if (responseData != null) {
            JSONArray itemList = responseData.getJSONArray("item_list");

            if (itemList != null) {
                for (int i = 0; i < itemList.size(); i++) {
                    JSONObject itemJson = itemList.getJSONObject(i);
                    OrderItem item = convertToOrderItem(itemJson);
                    items.add(item);
                }
            }
        }

        return items;
    }

    /**
     * 将Shopee订单商品转换为内部OrderItem对象
     */
    private OrderItem convertToOrderItem(JSONObject shopeeItem) {
        OrderItem item = new OrderItem();

        item.setPlatformProductId(String.valueOf(shopeeItem.getLong("item_id")));
        item.setPlatformSku(shopeeItem.getString("item_sku"));
        item.setProductName(shopeeItem.getString("item_name"));
        
        // 商品图片
        JSONObject itemImage = shopeeItem.getJSONObject("item_image");
        if (itemImage != null) {
            JSONObject original = itemImage.getJSONObject("original");
            if (original != null) {
                item.setProductImage(original.getString("url"));
            }
        }
        
        // 单价和数量
        item.setUnitPrice(new BigDecimal(shopeeItem.getString("model_original_price")));
        item.setQuantity(shopeeItem.getInteger("model_quantity_purchased"));

        // 计算总金额
        if (item.getUnitPrice() != null && item.getQuantity() != null) {
            item.setTotalAmount(item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())));
        }

        return item;
    }

    /**
     * 转换Shopee订单状态
     */
    private String convertShopeeStatus(String shopeeStatus) {
        switch (shopeeStatus) {
            case "UNPAID":
                return "pending_payment";
            case "PAID":
                return "pending_shipment";
            case "SHIPPED":
                return "shipped";
            case "COMPLETED":
                return "delivered";
            case "IN_CLAIM":
                return "refunded";
            case "CANCELLED":
                return "cancelled";
            default:
                return "unknown";
        }
    }

    /**
     * 生成内部订单号
     */
    private String generateInternalOrderNo() {
        return "SHP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
