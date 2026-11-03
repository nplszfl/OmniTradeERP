package com.crossborder.erp.inventory.alert.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模拟短信通道 —— <b>不投递任何短信</b>。
 *
 * <p>🔴 关键点：{@link #send(String, String)} 返回 {@code false}，
 * 而不是 {@code true}。</p>
 *
 * <p>📌 以前「模拟发送」的实现返回 {@code true}，导致上层把预警标成
 * {@code notified=true} —— 数据库里写着「已通知」，实际没人收到过。
 * <b>模拟实现必须诚实地报告「没发出去」</b>，
 * 否则就是往库里写假事实。</p>
 *
 * <p>接入真实网关时，新增一个实现 {@link SmsSender} 的 {@code @Component} 即可，
 * 本类会自动被替换（Spring 同类型 bean 注入，后定义的覆盖，或用
 * {@code @ConditionalOnMissingBean} 更明确）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Slf4j
@Component
public class SimulatedSmsSender implements SmsSender {

    @Override
    public boolean send(String phone, String content) {
        // 📌 用 warn 而不是 info —— 这是「有人以为通知发出去了，实际没发」的现场，
        //    必须显眼。info 级别在生产日志里基本没人看。
        log.warn("📱 模拟发送短信到 {}（未接入真实短信网关，实际**未投递**）: {}", phone, content);
        return false;
    }

    @Override
    public String providerName() {
        return "simulated";
    }
}
