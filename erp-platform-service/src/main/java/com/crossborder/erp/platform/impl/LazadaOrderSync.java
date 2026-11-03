package com.crossborder.erp.platform.impl;

import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.common.exception.PlatformApiException;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lazada订单同步实现
 * 使用Lazada Open Platform API
 *
 * <p>🔴 <b>本类曾是「假装成功」的空壳</b>：</p>
 * <ul>
 *   <li>{@code syncOrders} 永远返回**空列表**，却打 {@code log.info("同步完成, 共获取 0 条订单")}</li>
 *   <li>{@code getOrderByNo} 永远返回 <b>null</b></li>
 *   <li>{@code syncOrderItems} 永远返回**空列表**</li>
 *   <li>{@code validateConfig} 永远返回 <b>true</b> —— 等于**声称凭据有效**</li>
 * </ul>
 *
 * <p>后果：定时任务 {@code OrderSyncSchedule} 每 10 分钟跑一次，
 * 对 Lazada 店铺「同步成功，count=0」—— <b>没有任何异常、没有告警</b>，
 * 运营只会以为「今天 Lazada 没订单」。而平台对接其实根本没写。</p>
 *
 * <p>✅ 现在改成<strong>明确抛「未实现」</strong>，让故障可见。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Slf4j
@Component
public class LazadaOrderSync implements PlatformOrderSync {

    /** 未实现的错误码 —— 与「平台调用失败」区分开 */
    private static final String CODE_NOT_IMPLEMENTED = "NOT_IMPLEMENTED";

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.LAZADA;
    }

    @Override
    public List<Order> syncOrders(LocalDateTime startTime, LocalDateTime endTime, String shopId) {
        // TODO: 实现 Lazada Open Platform API 对接
        // 接口：https://open.lazada.com/doc/doc.htm#/?nav=2&articleId=823
        throw notImplemented("syncOrders", "GetOrders");
    }

    @Override
    public Order getOrderByNo(String platformOrderNo, String shopId) {
        // TODO: 实现 Lazada Open Platform API 对接
        // GetOrder接口
        throw notImplemented("getOrderByNo", "GetOrder");
    }

    @Override
    public List<OrderItem> syncOrderItems(String platformOrderNo, String shopId) {
        // TODO: 实现 Lazada Open Platform API 对接
        // GetOrderItems接口
        throw notImplemented("syncOrderItems", "GetOrderItems");
    }

    @Override
    public boolean validateConfig(String shopId) {
        // TODO: 实现 Lazada Open Platform API 对接
        //
        // 🔴 原来这里直接 return true —— 等于**无条件声称凭据有效**，
        //    配置校验形同虚设，失效的 token 要等真同步失败才发现。
        throw notImplemented("validateConfig", "GetSellerInfo");
    }

    private PlatformApiException notImplemented(String method, String api) {
        log.error("Lazada 订单同步未实现: method={}, 目标接口={}", method, api);
        return PlatformApiException.lazada(CODE_NOT_IMPLEMENTED,
                String.format("Lazada 订单同步尚未实现（%s → %s），"
                        + "该功能当前不可用；返回空结果会造成「同步成功但 0 条订单」的假象，"
                        + "因此这里明确抛错。", method, api));
    }
}
