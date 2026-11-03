package com.crossborder.erp.inventory.alert.service;

import com.crossborder.erp.inventory.alert.entity.InventoryAlert;
import com.crossborder.erp.inventory.alert.notify.SimulatedSmsSender;
import com.crossborder.erp.inventory.alert.notify.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 库存预警通知服务单元测试
 *
 * <p>🔴 <b>锁的是一个「假事实落库」型缺陷。</b></p>
 *
 * <p>以前 {@code sendSms()} 是一个只打日志的「模拟实现」，
 * 但 {@code sendSmsNotification()} 无条件 {@code return true}
 * → 调用方把预警标成 {@code notified=true}
 * → <b>列表页显示「已通知」，实际一个人都没收到</b>。</p>
 *
 * <p>📌 <b>核心原则：没发出去就不能说发出去了。</b>
 * 「模拟实现」和「真的投递」在下游无法区分 → 返回值必须如实报告。</p>
 */
class AlertNotifyServiceTest {

    private AlertNotifyService notifyService;
    private JavaMailSender mailSender;
    private SmsSender smsSender;

    @BeforeEach
    void setUp() {
        notifyService = new AlertNotifyService();
        mailSender = mock(JavaMailSender.class);
        smsSender = mock(SmsSender.class);
        when(smsSender.providerName()).thenReturn("test-provider");
        ReflectionTestUtils.setField(notifyService, "mailSender", mailSender);
        ReflectionTestUtils.setField(notifyService, "smsSender", smsSender);
        ReflectionTestUtils.setField(notifyService, "fromEmail", "noreply@example.com");
    }

    private InventoryAlert alert() {
        InventoryAlert alert = new InventoryAlert();
        alert.setId(1L);
        alert.setSku("SKU-001");
        alert.setProductName("智能手表");
        alert.setWarehouseName("汉堡仓");
        alert.setCurrentStock(3);
        alert.setAlertStock(10);
        alert.setAlertType("OUT");
        return alert;
    }

    // ============================================================
    // 短信通道
    // ============================================================

    @Test
    @DisplayName("短信通道未投递 → sendSmsNotification 返回 false（不能谎称已发送）")
    void smsNotDeliveredReturnsFalse() {
        when(smsSender.send(anyString(), anyString())).thenReturn(false);

        boolean sent = notifyService.sendSmsNotification(alert(), "[\"13800000000\"]");

        assertFalse(sent, "短信没投递就不能返回 true —— 否则 notified=true 是假事实");
    }

    @Test
    @DisplayName("短信通道确实投递 → sendSmsNotification 返回 true")
    void smsDeliveredReturnsTrue() {
        when(smsSender.send(anyString(), anyString())).thenReturn(true);

        boolean sent = notifyService.sendSmsNotification(alert(), "[\"13800000000\"]");

        assertTrue(sent);
        verify(smsSender).send(eq("13800000000"), anyString());
    }

    @Test
    @DisplayName("多个号码部分投递成功 → 只要有一条真发出去了就算已发送")
    void smsPartialDeliveryCountsAsSent() {
        when(smsSender.send(eq("13800000000"), anyString())).thenReturn(true);
        when(smsSender.send(eq("13900000000"), anyString())).thenReturn(false);

        boolean sent = notifyService.sendSmsNotification(alert(), "[\"13800000000\",\"13900000000\"]");

        assertTrue(sent);
    }

    @Test
    @DisplayName("手机号列表为空 → false，不调短信通道")
    void smsEmptyListReturnsFalse() {
        assertFalse(notifyService.sendSmsNotification(alert(), null));
        assertFalse(notifyService.sendSmsNotification(alert(), ""));
        verifyNoInteractions(smsSender);
    }

    @Test
    @DisplayName("手机号列表无有效号码（全是空白）→ false")
    void smsNoValidPhoneReturnsFalse() {
        boolean sent = notifyService.sendSmsNotification(alert(), "[\"  \",\"\"]");

        assertFalse(sent);
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("短信通道抛异常 → false（不中断，异常被记录）")
    void smsChannelThrowsReturnsFalse() {
        when(smsSender.send(anyString(), anyString())).thenThrow(new RuntimeException("网关超时"));

        boolean sent = notifyService.sendSmsNotification(alert(), "[\"13800000000\"]");

        assertFalse(sent);
    }

    // ============================================================
    // 邮件通道
    // ============================================================

    @Test
    @DisplayName("邮件发送成功 → true")
    void emailSentReturnsTrue() {
        boolean sent = notifyService.sendEmailNotification(alert(), "[\"ops@example.com\"]");

        assertTrue(sent);
        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("邮件发送抛异常 → false")
    void emailFailureReturnsFalse() {
        // 📌 必须写成 any(SimpleMailMessage.class)：JavaMailSender.send 有 4 个重载，
        //    裸 any() 会在 send(MimeMessagePreparator) 和 send(MimeMessagePreparator...) 之间歧义编译失败。
        doThrow(new MailSendException("SMTP 连不上")).when(mailSender)
                .send(any(org.springframework.mail.SimpleMailMessage.class));

        boolean sent = notifyService.sendEmailNotification(alert(), "[\"ops@example.com\"]");

        assertFalse(sent);
    }

    @Test
    @DisplayName("收件人列表无有效地址 → false（以前会 return true）")
    void emailNoValidRecipientReturnsFalse() {
        boolean sent = notifyService.sendEmailNotification(alert(), "[\"  \",\"\"]");

        assertFalse(sent, "0 个有效收件人时不能算已发送");
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("收件人列表为空 → false，不发邮件")
    void emailEmptyListReturnsFalse() {
        assertFalse(notifyService.sendEmailNotification(alert(), null));
        assertFalse(notifyService.sendEmailNotification(alert(), ""));
        verifyNoInteractions(mailSender);
    }

    // ============================================================
    // 汇总入口
    // ============================================================

    @Test
    @DisplayName("notifyType=SMS 且通道未投递 → sendNotification 返回 false")
    void sendNotificationSmsNotDelivered() {
        when(smsSender.send(anyString(), anyString())).thenReturn(false);

        boolean sent = notifyService.sendNotification(alert(), "SMS", null, "[\"13800000000\"]");

        assertFalse(sent);
    }

    @Test
    @DisplayName("notifyType=ALL 且只有模拟短信可用 → false（不能把「模拟」当成已通知）")
    void sendNotificationAllOnlySimulatedSms() {
        when(smsSender.send(anyString(), anyString())).thenReturn(false);
        // 邮件列表为空 → 邮件侧没发出去
        boolean sent = notifyService.sendNotification(alert(), "ALL", null, "[\"13800000000\"]");

        assertFalse(sent);
    }

    @Test
    @DisplayName("notifyType=ALL 且邮件真发出去了 → true")
    void sendNotificationAllEmailReallySent() {
        boolean sent = notifyService.sendNotification(alert(), "ALL", "[\"ops@example.com\"]", null);

        assertTrue(sent);
    }

    @Test
    @DisplayName("notifyType=SYSTEM → false（站内通知未接入，且不能标已通知）")
    void sendNotificationSystemReturnsFalse() {
        assertFalse(notifyService.sendNotification(alert(), "SYSTEM", null, null));
    }

    // ============================================================
    // 默认实现本身
    // ============================================================

    @Test
    @DisplayName("SimulatedSmsSender 本身必须返回 false —— 它不投递任何短信")
    void simulatedSenderReturnsFalse() {
        SimulatedSmsSender simulated = new SimulatedSmsSender();

        assertFalse(simulated.send("13800000000", "库存不足"));
        assertEquals("simulated", simulated.providerName());
    }
}
