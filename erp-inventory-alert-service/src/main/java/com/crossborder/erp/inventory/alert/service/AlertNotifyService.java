package com.crossborder.erp.inventory.alert.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.crossborder.erp.inventory.alert.entity.InventoryAlert;
import com.crossborder.erp.inventory.alert.notify.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存预警通知服务
 */
@Slf4j
@Service
public class AlertNotifyService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * 短信通道（默认是「模拟实现」，不投递任何短信）
     *
     * 📌 以前这里是 {@code private void sendSms(...)} —— 一个只打日志的桩，
     *    但上层 {@code sendSmsNotification()} 无条件 {@code return true}，
     *    导致预警被标成 {@code notified=true}：**库里写着「已通知」，实际没人收到**。
     *    现在短信走 {@link SmsSender} 抽象，真实投递与否由实现如实报告。
     */
    @Autowired
    private SmsSender smsSender;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromEmail;

    /**
     * 发送邮件通知
     */
    public boolean sendEmailNotification(InventoryAlert alert, String emailList) {
        try {
            if (emailList == null || emailList.isEmpty()) {
                log.warn("邮件通知列表为空，预警ID: {}", alert.getId());
                return false;
            }

            // 解析邮件列表
            List<String> emails;
            try {
                emails = JSON.parseArray(emailList, String.class);
            } catch (Exception e) {
                // 如果不是JSON格式，尝试直接使用
                emails = List.of(emailList.split(","));
            }

            String subject = buildEmailSubject(alert);
            String content = buildEmailContent(alert);

            int sent = 0;
            for (String email : emails) {
                email = email.trim();
                if (!email.isEmpty()) {
                    sendEmail(email, subject, content);
                    sent++;
                }
            }

            // 📌 边界：emailList 形如 "," / "  " → 解析出 0 个有效地址，
            //    以前照样 return true → notified=true，但一个人都没通知到。
            if (sent == 0) {
                log.warn("邮件通知无有效收件人，不视为已发送: alertId={}, emails={}",
                        alert.getId(), emailList);
                return false;
            }

            log.info("邮件通知发送成功: alertId={}, emails={}, count={}", alert.getId(), emailList, sent);
            return true;
        } catch (Exception e) {
            log.error("邮件通知发送失败: alertId={}, error={}", alert.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送单封邮件
     */
    private void sendEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    /**
     * 发送短信通知
     *
     * <p>🔴 <b>返回值必须是「真的投递出去了」</b>，而不是「跑了一遍流程」。</p>
     *
     * <p>📌 以前这里是「模拟实现」（只打一行日志）+ 无条件 {@code return true}
     * → 上层把预警标成 {@code notified=true}，
     * <b>数据库里写着「已通知」，实际一个人都没收到</b>。</p>
     *
     * <p>现在短信走 {@link SmsSender}：默认实现是
     * {@link com.crossborder.erp.inventory.alert.notify.SimulatedSmsSender}，
     * 它诚实地返回 {@code false}。接入真实网关后会自动换成真实实现。</p>
     */
    public boolean sendSmsNotification(InventoryAlert alert, String phoneList) {
        try {
            if (phoneList == null || phoneList.isEmpty()) {
                log.warn("短信通知列表为空，预警ID: {}", alert.getId());
                return false;
            }

            // 解析手机号列表
            List<String> phones;
            try {
                phones = JSON.parseArray(phoneList, String.class);
            } catch (Exception e) {
                phones = List.of(phoneList.split(","));
            }

            String smsContent = buildSmsContent(alert);

            int delivered = 0;
            int attempted = 0;
            for (String phone : phones) {
                phone = phone.trim();
                if (!phone.isEmpty()) {
                    attempted++;
                    if (smsSender.send(phone, smsContent)) {
                        delivered++;
                    }
                }
            }

            // 📌 一条都没投递出去 → 不能算「已通知」
            if (delivered == 0) {
                log.warn("短信通知未实际投递（provider={}, 尝试={} 条），不视为已发送: alertId={}, phones={}",
                        smsSender.providerName(), attempted, alert.getId(), phoneList);
                return false;
            }

            log.info("短信通知发送成功: alertId={}, phones={}, delivered={}/{}",
                    alert.getId(), phoneList, delivered, attempted);
            return true;
        } catch (Exception e) {
            log.error("短信通知发送失败: alertId={}, error={}", alert.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送站内通知（模拟实现）
     * 实际项目中可以通过WebSocket、消息队列等实现
     */
    public void sendSystemNotification(InventoryAlert alert, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            log.warn("站内通知用户列表为空，预警ID: {}", alert.getId());
            return;
        }

        String notification = buildSystemNotification(alert);

        for (Long userId : userIds) {
            // 实际项目中保存到数据库或通过WebSocket推送
            log.info("🔔 站内通知用户 {}: {}", userId, notification);
        }

        log.info("站内通知发送成功: alertId={}, userCount={}", alert.getId(), userIds.size());
    }

    /**
     * 发送所有类型的通知
     */
    /**
     * 发送通知
     *
     * @return 是否**真的**发出去过。
     *         ⚠️ 以前这里是 void，调用方无条件把预警标成「已通知」——
     *            但邮件列表为空、短信是模拟实现、发送抛异常被内部 catch 掉，
     *            三种情况下其实**一个人都没通知到**，
     *            记录上却写着 notified=true（列表页显示「已通知」，实际没人收到）。
     *            现在返回真实结果，调用方据此决定要不要标记。
     */
    public boolean sendNotification(InventoryAlert alert, String notifyType, String emailList, String phoneList) {
        if (notifyType == null) {
            notifyType = "ALL";
        }

        switch (notifyType) {
            case "EMAIL":
                return sendEmailNotification(alert, emailList);
            case "SMS":
                return sendSmsNotification(alert, phoneList);
            case "SYSTEM":
                // 站内通知需要用户ID列表，这里暂时跳过
                log.info("站内通知需要用户ID列表，请调用sendSystemNotification方法");
                return false;
            case "ALL":
            default:
                boolean emailSent = sendEmailNotification(alert, emailList);
                boolean smsSent = sendSmsNotification(alert, phoneList);
                return emailSent || smsSent;
        }
    }

    /**
     * 构建邮件主题
     */
    private String buildEmailSubject(InventoryAlert alert) {
        String prefix = "OUT".equals(alert.getAlertType()) ? "【紧急】缺货预警" : "【提醒】库存预警";
        return String.format("%s - %s", prefix, alert.getSku());
    }

    /**
     * 构建邮件内容
     */
    private String buildEmailContent(InventoryAlert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("您好，检测到库存预警：\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("产品信息\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("SKU: %s\n", alert.getSku()));
        sb.append(String.format("产品名称: %s\n", alert.getProductName()));
        sb.append(String.format("仓库: %s\n", alert.getWarehouseName()));
        sb.append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("库存情况\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("当前库存: %d\n", alert.getCurrentStock()));
        sb.append(String.format("预警库存: %d\n", alert.getAlertStock()));
        sb.append(String.format("安全库存: %d\n", alert.getSafeStock()));
        sb.append(String.format("预警类型: %s\n", alert.getAlertType()));
        sb.append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("预警消息\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(alert.getMessage());
        sb.append("\n\n");
        sb.append("请及时处理！\n");
        return sb.toString();
    }

    /**
     * 构建短信内容
     */
    private String buildSmsContent(InventoryAlert alert) {
        return String.format("【ERP预警】%s 库存不足！当前库存:%d，预警值:%d，请及时补货。",
                alert.getSku(), alert.getCurrentStock(), alert.getAlertStock());
    }

    /**
     * 构建站内通知内容
     */
    private String buildSystemNotification(InventoryAlert alert) {
        return String.format("库存预警: %s - %s，当前库存 %d，低于预警值 %d",
                alert.getSku(), alert.getProductName(), alert.getCurrentStock(), alert.getAlertStock());
    }
}