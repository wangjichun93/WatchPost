package com.watchpost.alerter.converge;

import com.watchpost.common.enums.AlertState;
import com.watchpost.common.enums.Severity;
import com.watchpost.common.spi.RealtimeStore;
import com.watchpost.warehouse.entity.AlertEntity;
import com.watchpost.warehouse.entity.AlertEventEntity;
import com.watchpost.warehouse.entity.ThresholdRuleEntity;
import com.watchpost.warehouse.repository.AlertEventRepository;
import com.watchpost.warehouse.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 告警收敛服务 (PRD 04: 告警状态机与收敛)。
 *
 * 收敛三关: 连续 N 次 (滑动窗口计数) → 抑制窗口 (同指标 suppressSec 不重发) → 静默 (维护窗口/手动静默)。
 * 合并: 同 fingerprint (monitor+group+field+rule) 的重复命中合并为一条活动告警, 仅递增 hit_count。
 * 恢复: 回到正常区间单独发恢复邮件, 转历史告警。
 *
 * 状态机: NORMAL→PENDING→TRIGGERED→NOTIFYING→ACKNOWLEDGED→RECOVERED→CLOSED
 * 活动态计数/抑制窗口存 Redis (RealtimeStore), 告警主体/事件持久化 PostgreSQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertConvergenceService {

    private final AlertRepository alertRepo;
    private final AlertEventRepository eventRepo;
    private final RealtimeStore realtimeStore;

    /** 告警编号序列 (A-xxxx); 生产建议用 DB 序列, 此处单机内存自增 + 时间戳兜底。 */
    private final AtomicLong alertSeq = new AtomicLong(System.currentTimeMillis() % 10000);

    /** 活动告警状态 (收敛查找用)。 */
    private static final List<AlertState> ACTIVE_STATES =
            List.of(AlertState.PENDING, AlertState.TRIGGERED, AlertState.NOTIFYING, AlertState.ACKNOWLEDGED);

    /**
     * 处理一次「命中」(规则求值为越限)。
     * @return 需要通知的告警 (通过收敛三关), 否则 empty
     */
    @Transactional
    public Optional<AlertEntity> onHit(HitContext ctx) {
        String fingerprint = fingerprint(ctx);
        ThresholdRuleEntity rule = ctx.rule();

        // 1. 连续 N 次: 滑动窗口计数
        long hits = realtimeStore.incrHit(fingerprint, rule.getWindowSec());
        if (hits < rule.getConsecutiveN()) {
            log.debug("hit {}/{} below consecutive threshold, fingerprint={}", hits, rule.getConsecutiveN(), fingerprint);
            return Optional.empty();
        }

        // 2. 查找/创建活动告警 (合并重复命中)
        AlertEntity alert = alertRepo.findByFingerprintAndStateIn(fingerprint, ACTIVE_STATES)
                .orElseGet(() -> newAlert(ctx, fingerprint));
        boolean isNew = alert.getId() == null;
        alert.setHitCount((int) hits);
        alert.setCurrentValue(ctx.currentValue());
        alert.setLastTriggerAt(OffsetDateTime.now());
        if (isNew) alert.setFirstTriggerAt(OffsetDateTime.now());

        // 已确认的告警再次命中: 保持 ACKNOWLEDGED, 不重复通知
        if (alert.getState() == AlertState.ACKNOWLEDGED) {
            alertRepo.save(alert);
            return Optional.empty();
        }

        // 3. 静默检查 (维护窗口/手动静默)
        if (alert.isSilenced()) {
            alert.setState(AlertState.TRIGGERED);
            alertRepo.save(alert);
            recordEvent(alert, alert.getState(), AlertState.TRIGGERED, "system", "silenced, skip notify");
            log.debug("alert silenced, fingerprint={}", fingerprint);
            return Optional.empty();
        }

        // 状态推进: PENDING/新建 → TRIGGERED
        AlertState prev = alert.getState();
        alert.setState(AlertState.TRIGGERED);
        alertRepo.save(alert);
        if (isNew || prev != AlertState.TRIGGERED) {
            recordEvent(alert, prev, AlertState.TRIGGERED, "system", "hit " + hits + " times");
        }

        // 4. 抑制窗口: 同指标 suppressSec 内不重发
        if (!realtimeStore.acquireSuppress(fingerprint, rule.getSuppressSec())) {
            log.debug("suppressed within window, fingerprint={}", fingerprint);
            return Optional.empty();
        }

        // 通过收敛三关 → 通知中
        alert.setState(AlertState.NOTIFYING);
        alertRepo.save(alert);
        recordEvent(alert, AlertState.TRIGGERED, AlertState.NOTIFYING, "system", "dispatch notification");
        return Optional.of(alert);
    }

    /**
     * 处理一次「恢复」(规则求值回到正常区间)。
     * @return 需要发恢复邮件的告警, 否则 empty
     */
    @Transactional
    public Optional<AlertEntity> onRecover(RecoverContext ctx) {
        String fingerprint = fingerprint(ctx.monitorId(), ctx.group(), ctx.field(), ctx.ruleId());
        realtimeStore.resetHit(fingerprint);
        Optional<AlertEntity> opt = alertRepo.findByFingerprintAndStateIn(fingerprint, ACTIVE_STATES);
        if (opt.isEmpty()) return Optional.empty();
        AlertEntity alert = opt.get();
        // 仅当之前已通知过 (NOTIFYING/ACKNOWLEDGED) 才发恢复邮件
        boolean shouldNotify = alert.getState() == AlertState.NOTIFYING || alert.getState() == AlertState.ACKNOWLEDGED;
        AlertState prev = alert.getState();
        alert.setState(AlertState.RECOVERED);
        alert.setRecoveredAt(OffsetDateTime.now());
        alertRepo.save(alert);
        recordEvent(alert, prev, AlertState.RECOVERED, "system", "back to normal");
        // 恢复后关闭 (转历史)
        alert.setState(AlertState.CLOSED);
        alert.setClosedAt(OffsetDateTime.now());
        alertRepo.save(alert);
        recordEvent(alert, AlertState.RECOVERED, AlertState.CLOSED, "system", "archived");
        return shouldNotify ? Optional.of(alert) : Optional.empty();
    }

    /** 手动确认告警 (值班人操作, 记录操作人+时间+审计)。 */
    @Transactional
    public AlertEntity acknowledge(String alertNo, String operator) {
        AlertEntity alert = alertRepo.findByAlertNo(alertNo)
                .orElseThrow(() -> new IllegalArgumentException("alert not found: " + alertNo));
        AlertState prev = alert.getState();
        alert.setState(AlertState.ACKNOWLEDGED);
        alert.setAckAt(OffsetDateTime.now());
        alertRepo.save(alert);
        recordEvent(alert, prev, AlertState.ACKNOWLEDGED, operator, "manual acknowledge");
        return alert;
    }

    /** 手动静默 (如静默1h)。 */
    @Transactional
    public AlertEntity silence(String alertNo, int seconds, String operator) {
        AlertEntity alert = alertRepo.findByAlertNo(alertNo)
                .orElseThrow(() -> new IllegalArgumentException("alert not found: " + alertNo));
        alert.setSilencedUntil(OffsetDateTime.now().plusSeconds(seconds));
        alertRepo.save(alert);
        recordEvent(alert, alert.getState(), alert.getState(), operator, "silence " + seconds + "s");
        return alert;
    }

    private AlertEntity newAlert(HitContext ctx, String fingerprint) {
        AlertEntity a = new AlertEntity();
        a.setAlertNo("A-" + alertSeq.incrementAndGet());
        a.setMonitorId(ctx.monitorId());
        a.setMetricFieldId(ctx.metricFieldId());
        a.setRuleId(ctx.rule().getId());
        a.setMetricGroup(ctx.group());
        a.setFieldName(ctx.field());
        a.setState(AlertState.PENDING);
        a.setSeverity(ctx.rule().getSeverity());
        a.setCurrentValue(ctx.currentValue());
        a.setThresholdDesc(ctx.thresholdDesc());
        a.setHitCount(1);
        a.setFingerprint(fingerprint);
        a.setNotifyGroupId(ctx.rule().getNotifyPolicyId());
        return a;
    }

    private void recordEvent(AlertEntity alert, AlertState from, AlertState to, String operator, String note) {
        AlertEventEntity e = new AlertEventEntity();
        e.setAlertId(alert.getId());
        e.setFromState(from);
        e.setToState(to);
        e.setOperator(operator);
        e.setNote(note);
        eventRepo.save(e);
    }

    private String fingerprint(HitContext ctx) {
        return fingerprint(ctx.monitorId(), ctx.group(), ctx.field(), ctx.rule().getId());
    }
    private String fingerprint(long monitorId, String group, String field, Long ruleId) {
        return monitorId + "|" + group + "|" + field + "|" + ruleId;
    }

    /** 命中上下文。 */
    public record HitContext(long monitorId, Long metricFieldId, String group, String field,
                             String currentValue, String thresholdDesc, ThresholdRuleEntity rule) {}

    /** 恢复上下文。 */
    public record RecoverContext(long monitorId, String group, String field, Long ruleId, Severity severity) {}
}
