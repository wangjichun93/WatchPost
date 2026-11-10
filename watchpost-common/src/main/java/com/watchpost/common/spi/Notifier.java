package com.watchpost.common.spi;

import java.util.List;
import java.util.Map;

/**
 * 通知渠道 SPI。v1 仅 EmailNotifier; WebhookNotifier 预留 (PRD: IM/短信不在 v1)。
 */
public interface Notifier {

    /** 渠道标识: EMAIL / WEBHOOK。 */
    String channel();

    /**
     * 发送一条通知。
     * @param recipients 收件人
     * @param subject    标题
     * @param content    渲染后的正文 (HTML)
     * @param context    模板变量 (告警对象/指标/当前值/阈值/链接等)
     * @return 投递结果
     */
    NotifyResult send(List<String> recipients, String subject, String content, Map<String, Object> context);

    /** 发送测试邮件 (可观测性: PRD 要求)。 */
    default NotifyResult sendTest(String recipient) {
        return send(List.of(recipient), "[望哨] 测试邮件", "这是一封来自望哨 WATCHPOST 的测试邮件。", Map.of());
    }

    record NotifyResult(boolean success, String messageId, String error) {
        public static NotifyResult ok(String id) { return new NotifyResult(true, id, null); }
        public static NotifyResult fail(String err) { return new NotifyResult(false, null, err); }
    }
}
