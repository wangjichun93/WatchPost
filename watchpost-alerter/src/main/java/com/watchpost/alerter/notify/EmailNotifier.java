package com.watchpost.alerter.notify;

import com.watchpost.common.spi.CryptoService;
import com.watchpost.common.spi.Notifier;
import com.watchpost.warehouse.entity.SmtpConfigEntity;
import com.watchpost.warehouse.repository.SmtpConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 邮件通知实现 (PRD 05: SMTP host/port/STARTTLS/发件人/授权码加密)。
 * SMTP 配置从 wp_smtp_config 动态加载, 授权码 AES-256-GCM 解密后注入 (用后即弃, 不落日志)。
 * 配置变更即时生效 (每次发送重建 sender, 配置缓存可后续优化)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotifier implements Notifier {

    private final SmtpConfigRepository smtpRepo;
    private final CryptoService crypto;

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public NotifyResult send(List<String> recipients, String subject, String content, Map<String, Object> context) {
        if (recipients == null || recipients.isEmpty()) {
            return NotifyResult.fail("no recipients");
        }
        SmtpConfigEntity cfg = smtpRepo.findFirstByEnabledTrueOrderByIdDesc().orElse(null);
        if (cfg == null) {
            return NotifyResult.fail("SMTP not configured");
        }
        try {
            JavaMailSenderImpl sender = buildSender(cfg);
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(cfg.getSender());
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(content, true);   // HTML
            sender.send(msg);
            String id = msg.getMessageID();
            log.info("email sent to {} subject={}", recipients.size(), subject);
            return NotifyResult.ok(id);
        } catch (Exception e) {
            log.error("email send failed: {}", e.getMessage());
            return NotifyResult.fail(e.getMessage());
        }
    }

    private JavaMailSenderImpl buildSender(SmtpConfigEntity cfg) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getHost());
        sender.setPort(cfg.getPort());
        sender.setUsername(cfg.getSender());
        // 授权码解密 (敏感, 不日志)
        sender.setPassword(crypto.decrypt(cfg.getAuthCipher()));
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (Boolean.TRUE.equals(cfg.getStarttls())) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", cfg.getHost());
        }
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        return sender;
    }
}
