package com.crossborder.erp.inventory.alert.notify;

/**
 * 短信发送通道（扩展点）。
 *
 * <p>🔴 <b>这个接口存在的唯一原因，是修一个「假事实落库」型缺陷。</b></p>
 *
 * <p><b>缺陷本体：</b>{@code AlertNotifyService.sendSms()} 是一个「模拟实现」——
 * 只打一行 {@code log.info("📱 模拟发送短信到 {}: {}")} 就返回。
 * 但上层 {@code sendSmsNotification()} 无论有没有真的发出去，
 * <b>一律 {@code return true}</b>，调用方据此把预警标成
 * {@code notified=true}（列表页显示「已通知」）。</p>
 *
 * <p>后果：规则配置成 {@code notifyType=SMS} 时，
 * <b>数据库里写着「已通知」，实际上一个人都没收到</b>，而且没有任何报错。</p>
 *
 * <p>📌 <b>核心原则：返回「没发出去」时，必须让调用方知道。</b>
 * 「模拟实现」和「真的发出去了」在下游无法区分 →
 * 那就不能都返回 true。</p>
 *
 * <p><b>接入真实网关的方式：</b>新建一个 {@code @Component} 实现本接口，
 * 容器里就会自动替换掉 {@link SimulatedSmsSender}
 * （本项目其它地方也是这个模式：{@code @ConditionalOnMissingBean} / 自动装配）。
 * 调用方代码零改动。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
public interface SmsSender {

    /**
     * 发送一条短信。
     *
     * @param phone   手机号
     * @param content 短信正文
     * @return {@code true} = 短信**确实已投递**；
     *         {@code false} = 未投递（未接入网关 / 网关返回失败 / 参数非法）
     */
    boolean send(String phone, String content);

    /**
     * 通道标识，用于日志与告警定位（如 {@code aliyun} / {@code tencent} / {@code simulated}）
     */
    String providerName();
}
