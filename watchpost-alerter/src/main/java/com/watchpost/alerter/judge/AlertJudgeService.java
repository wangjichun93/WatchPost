package com.watchpost.alerter.judge;

import com.watchpost.alerter.converge.AlertConvergenceService;
import com.watchpost.alerter.notify.NotifyRouter;
import com.watchpost.alerter.rule.RuleEngine;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.spi.MetricQueue;
import com.watchpost.common.spi.RealtimeStore;
import com.watchpost.common.spi.TimeSeriesStore;
import com.watchpost.warehouse.entity.AlertEntity;
import com.watchpost.warehouse.entity.ThresholdRuleEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 告警判定服务 (PRD 04: 采集值落盘后立即做规则判定)。
 * 订阅 MetricQueue, 对每条采集结果的每个字段: 解析生效规则 → JEXL 求值 → 收敛状态机 → 通知路由。
 * 判定只读最新值 + 滑动窗口, 不做全量历史回扫 (环比基线从 TSDB 短窗口聚合)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertJudgeService {

    private final MetricQueue metricQueue;
    private final RuleEngine ruleEngine;
    private final AlertConvergenceService convergence;
    private final NotifyRouter notifyRouter;
    private final RealtimeStore realtimeStore;
    private final TimeSeriesStore timeSeriesStore;

    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() {
        metricQueue.subscribe("alerter", this::judge);
        log.info("alert judge service subscribed to metric queue");
    }

    /** 判定一条采集结果。永不抛出 (单条异常不影响队列消费)。 */
    public void judge(MetricRecord record) {
        if (record == null || record.getValues() == null) return;
        // 采集失败: 主机可达性等规则由 connFailCount 驱动, 此处仅处理成功采集的字段值
        if (!record.isSuccess()) {
            judgeUnreachable(record);
            return;
        }
        String appType = record.getAppType() == null ? null : record.getAppType().name();
        for (MetricValue value : record.getValues()) {
            try {
                judgeField(record, appType, value);
            } catch (Exception e) {
                log.debug("judge field error monitorId={} {}#{}: {}",
                        record.getMonitorId(), value.getMetricGroup(), value.getFieldName(), e.getMessage());
            }
        }
    }

    private void judgeField(MetricRecord record, String appType, MetricValue value) {
        List<ThresholdRuleEntity> rules = ruleEngine.resolveRules(
                record.getMonitorId(), appType, value.getMetricGroup(), value.getFieldName());
        if (rules.isEmpty()) return;

        // 取最高优先级规则 (实例>类型>全局); 同字段可能有多条 (如 disk 85%警告/95%严重), 逐条判定
        for (ThresholdRuleEntity rule : rules) {
            Double baseline = needBaseline(rule) ? loadBaseline(record, value, rule) : null;
            boolean hit = ruleEngine.evaluate(rule, value, baseline);
            if (hit) {
                AlertConvergenceService.HitContext ctx = new AlertConvergenceService.HitContext(
                        record.getMonitorId(), null, value.getMetricGroup(), value.getFieldName(),
                        String.valueOf(value.getDoubleValue()), ruleEngine.describeThreshold(rule), rule);
                Optional<AlertEntity> toNotify = convergence.onHit(ctx);
                toNotify.ifPresent(alert -> notifyRouter.route(alert, record, value, rule, false));
            } else {
                // 恢复检测: 仅当存在活动告警时触发
                AlertConvergenceService.RecoverContext ctx = new AlertConvergenceService.RecoverContext(
                        record.getMonitorId(), value.getMetricGroup(), value.getFieldName(),
                        rule.getId(), rule.getSeverity());
                Optional<AlertEntity> recovered = convergence.onRecover(ctx);
                recovered.ifPresent(alert -> notifyRouter.route(alert, record, value, rule, true));
            }
        }
    }

    /** 采集失败 → 主机可达性规则 (host.reachable=0, 连续2次严重)。 */
    private void judgeUnreachable(MetricRecord record) {
        String appType = record.getAppType() == null ? null : record.getAppType().name();
        MetricValue reachable = MetricValue.builder()
                .metricGroup("host").fieldName("reachable")
                .fieldType(com.watchpost.common.enums.FieldType.DOUBLE)
                .doubleValue(0.0).build();
        List<ThresholdRuleEntity> rules = ruleEngine.resolveRules(record.getMonitorId(), appType, "host", "reachable");
        for (ThresholdRuleEntity rule : rules) {
            if (ruleEngine.evaluate(rule, reachable, null)) {
                AlertConvergenceService.HitContext ctx = new AlertConvergenceService.HitContext(
                        record.getMonitorId(), null, "host", "reachable",
                        "unreachable", ruleEngine.describeThreshold(rule), rule);
                convergence.onHit(ctx).ifPresent(alert -> notifyRouter.route(alert, record, reachable, rule, false));
            }
        }
    }

    private boolean needBaseline(ThresholdRuleEntity rule) {
        return rule.getOperator() == com.watchpost.common.enums.CompareOp.DELTA_PCT;
    }

    /** 环比基线: 从 TSDB 取 baselineWindowSec 窗口的平均值。 */
    private Double loadBaseline(MetricRecord record, MetricValue value, ThresholdRuleEntity rule) {
        try {
            long now = record.getTimestamp();
            long windowMs = 300_000L;   // 默认 5min 基线
            var data = timeSeriesStore.queryInterval(record.getMonitorId(), value.getMetricGroup(),
                    value.getFieldName(), now - windowMs, now, 60);
            var pts = data.get(value.getMetricGroup() + "#" + value.getFieldName());
            if (pts == null || pts.isEmpty()) return null;
            return pts.stream().filter(p -> p.avg() != null).mapToDouble(TimeSeriesStore.Point::avg)
                    .average().orElse(Double.NaN);
        } catch (Exception e) {
            return null;
        }
    }
}
