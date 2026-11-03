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
 * eBay订单同步实现
 * 使用eBay Trading API
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
 * 对 eBay 店铺「同步成功，count=0」—— <b>没有任何异常、没有告警</b>，
 * 运营只会以为「今天 eBay 没订单」。而平台对接其实根本没写。</p>
 *
 * <p>✅ 现在改成<strong>明确抛「未实现」</strong>：</p>
 * <ul>
 *   <li>定时任务会打 {@code log.error("同步平台订单失败: ...")} —— 故障可见</li>
 *   <li>手动触发同步的接口会返回 502 而不是「成功但 0 条」</li>
 *   <li>错误码用 {@code NOT_IMPLEMENTED}，和「平台返回了空结果」能区分开</li>
 * </ul>
 *
 * <p>⚠️ 注意：<b>不要用「返回空集合」代替抛异常</b>。
 * 空集合和「真的没有订单」在下游完全无法区分 —— 这就是静默失败。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Slf4j
@Component
public class EbayOrderSync implements PlatformOrderSync {

    /** 未实现的错误码 —— 与「平台调用失败」区分开 */
    private static final String CODE_NOT_IMPLEMENTED = "NOT_IMPLEMENTED";

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.EBAY;
    }

    @Override
    public List<Order> syncOrders(LocalDateTime startTime, LocalDateTime endTime, String shopId) {
        // TODO: 实现 eBay Trading API 对接
        // 接口：https://developer.ebay.com/devzone/xml/docs/reference/ebay/GetOrders.html
        // 需要使用eBay XML API或RESTful API
        throw notImplemented("syncOrders", "GetOrders");
    }

    @Override
    public Order getOrderByNo(String platformOrderNo, String shopId) {
        // TODO: 实现 eBay Trading API 对接
        // GetOrderCall接口
        throw notImplemented("getOrderByNo", "GetOrder");
    }

    @Override
    public List<OrderItem> syncOrderItems(String platformOrderNo, String shopId) {
        // TODO: 实现 eBay Trading API 对接
        // OrderItem包含在GetOrders响应中
        throw notImplemented("syncOrderItems", "GetOrderItems");
    }

    @Override
    public boolean validateConfig(String shopId) {
        // TODO: 实现 eBay Trading API 对接
        // 可以调用GeteBayOfficialTime接口验证Token有效性
        //
        // 🔴 原来这里直接 return true —— 等于**无条件声称凭据有效**，
        //    配置校验形同虚设，失效的 token 要等真同步失败才发现。
        throw notImplemented("validateConfig", "GeteBayOfficialTime");
    }

    private PlatformApiException notImplemented(String method, String api) {
        log.error("eBay 订单同步未实现: method={}, 目标接口={}, shopId 待定", method, api);
        return PlatformApiException.ebay(CODE_NOT_IMPLEMENTED,
                String.format("eBay 订单同步尚未实现（%s → %s），"
                        + "该功能当前不可用；返回空结果会造成「同步成功但 0 条订单」的假象，"
                        + "因此这里明确抛错。", method, api));
    }
}
