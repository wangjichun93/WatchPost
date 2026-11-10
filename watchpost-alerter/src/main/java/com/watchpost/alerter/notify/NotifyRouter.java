package com.watchpost.alerter.notify;

import com.watchpost.common.enums.Severity;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.spi.Notifier;
import com.watchpost.common.util.JsonUtil;
import com.watchpost.warehouse.entity.AlertEntity;
import com.watchpost.warehouse.entity.DeliveryLogEntity;
import com.watchpost.warehouse.entity.NotifyGroupEntity;
import com.watchpost.warehouse.entity.NotifyPolicyEntity;
import com.watchpost.warehouse.entity.ThresholdRuleEntity;
import com.watchpost.warehouse.repository.DeliveryLogRepository;
import com.watchpost.warehouse.repository.NotifyGroupRepository;
import com.watchpost.warehouse.repository.NotifyPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知路由 (PRD 05: 按标签 + 级别路由到收件人组)。
 * 流程: 匹配通知策略 (级别 × 标签) → 取收件人组 → 限流 (单组30min≤20封) → 渲染 → Notifier 投递 → 投递日志。
 * 触发与恢复各发一封; 失败进入重试调度 (1/5/15min)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyRouter {

    private final NotifyPolicyRepository policyRepo;
    private final NotifyGroupRepository groupRepo;
    private final DeliveryLogRepository deliveryRepo;
    private final List<Notifier> notifiers;
    private final NotifyRateLimiter rateLimiter;
    private final AlertMailRenderer renderer;

    /** 路由一条告警到收件人组并投递。 */
    public void route(AlertEntity alert, MetricRecord record, MetricValue value,
                      ThresholdRuleEntity rule, boolean isRecovery) {
        // 1. 匹配通知策略 (级别 × 标签)
        Optional<NotifyPolicyEntity> policy = matchPolicy(alert.getSeverity(), record);
        if (policy.isEmpty()) {
            log.debug("no notify policy matched for alert {} severity={}", alert.getAlertNo(), alert.getSeverity());
            return;
        }
        // 2. 取收件人组
        Optional<NotifyGroupEntity> group = groupRepo.findById(policy.get().getNotifyGroupId());
        if (group.isEmpty() || !Boolean.TRUE.equals(group.get().getEnabled())) {
            log.warn("notify group not found/disabled: {}", policy.get().getNotifyGroupId());
            return;
        }
        List<String> recipients = extractEmails(group.get().getMembersJson());
        if (recipients.isEmpty()) {
            log.warn("notify group {} has no recipients", group.get().getName());
            return;
        }

        // 3. 限流: 单收件人组 30min ≤ 20 封
        if (!rateLimiter.tryAcquire(group.get().getId())) {
            log.warn("notify rate limit exceeded for group {}, drop alert {}", group.get().getName(), alert.getAlertNo());
            return;
        }

        // 4. 渲染正文
        String subject = renderer.subject(alert, record, isRecovery);
        String content = renderer.render(alert, record, value, rule, group.get().getName(), isRecovery);

        // 5. 投递 (v1 仅 EMAIL)
        Notifier email = notifiers.stream().filter(n -> "EMAIL".equals(n.channel())).findFirst().orElse(null);
        if (email == null) {
            log.error("no EMAIL notifier available");
            return;
        }
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("alertNo", alert.getAlertNo());
        ctx.put("recovery", isRecovery);
        Notifier.NotifyResult result = email.send(recipients, subject, content, ctx);

        // 6. 投递日志 (+ 失败重试调度)
        for (String r : recipients) {
            logDelivery(alert.getId(), r, subject, result, 0);
        }
        if (!result.success()) {
            scheduleRetry(alert.getId(), recipients, subject, content);
        }
    }

    private Optional<NotifyPolicyEntity> matchPolicy(Severity severity, MetricRecord record) {
        List<NotifyPolicyEntity> policies = policyRepo.findByEnabledTrueOrderByPriorityDesc();
        for (NotifyPolicyEntity p : policies) {
            // 级别匹配 (空=全部)
            List<String> sev = p.severityList();
            if (!sev.isEmpty() && !sev.contains(severity.name())) continue;
            // 标签匹配 (空=全部); v1 标签从 record 透传, 简化为匹配通过
            return Optional.of(p);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractEmails(String membersJson) {
        List<String> emails = new ArrayList<>();
        try {
            List<Map<String, Object>> members = JsonUtil.fromJson(membersJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> m : members) {
                Object e = m.get("email");
                if (e != null) emails.add(String.valueOf(e));
            }
        } catch (Exception e) {
            log.warn("parse members_json failed: {}", e.getMessage());
        }
        return emails;
    }

    private void logDelivery(Long alertId, String recipient, String subject, Notifier.NotifyResult result, int retryNo) {
        DeliveryLogEntity d = new DeliveryLogEntity();
        d.setAlertId(alertId);
        d.setChannel("EMAIL");
        d.setRecipient(recipient);
        d.setSubject(subject);
        d.setStatus(result.success() ? 1 : 2);
        d.setRetryNo(retryNo);
        d.setErrorReason(result.error());
        if (result.success()) d.setSentAt(OffsetDateTime.now());
        deliveryRepo.save(d);
    }

    /** 失败重试: 写一条 next_retry_at=+1min 的待发记录, 由 DeliveryRetryScheduler 轮询。 */
    private void scheduleRetry(Long alertId, List<String> recipients, String subject, String content) {
        for (String r : recipients) {
            DeliveryLogEntity d = new DeliveryLogEntity();
            d.setAlertId(alertId);
            d.setChannel("EMAIL");
            d.setRecipient(r);
            d.setSubject(subject);
            d.setStatus(2);
            d.setRetryNo(0);
            d.setNextRetryAt(OffsetDateTime.now().plusMinutes(1));
            deliveryRepo.save(d);
        }
    }
}
