package com.watchpost.alerter.notify;

import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MetricValue;
import com.watchpost.warehouse.entity.AlertEntity;
import com.watchpost.warehouse.entity.ThresholdRuleEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 告警邮件渲染器 (PRD 05 正文模板)。
 * 标题: [级别] 对象 · 指标越限 / 恢复。
 * 正文: 监控对象/指标字段/当前值·阈值/连续命中·窗口/首次触发/级别·策略组/查看详情/静默链接。
 * 使用 Thymeleaf TEXT 模板 (classpath:templates/mail/alert-email.txt)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertMailRenderer {

    private final TemplateEngine templateEngine;
    private final AlertLinkBuilder linkBuilder;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String subject(AlertEntity alert, MetricRecord record, boolean isRecovery) {
        String sev = "[" + alert.getSeverity().label() + "]";
        String obj = record != null ? record.getMonitorName() : ("monitor#" + alert.getMonitorId());
        String metric = alert.getMetricGroup() + "." + alert.getFieldName();
        return isRecovery
                ? sev + " " + obj + " · " + metric + " 已恢复"
                : sev + " " + obj + " · " + metric + " 越限";
    }

    public String render(AlertEntity alert, MetricRecord record, MetricValue value,
                         ThresholdRuleEntity rule, String groupName, boolean isRecovery) {
        Context ctx = new Context();
        ctx.setVariable("isRecovery", isRecovery);
        ctx.setVariable("severity", alert.getSeverity().label());
        ctx.setVariable("monitorName", record != null ? record.getMonitorName() : ("monitor#" + alert.getMonitorId()));
        ctx.setVariable("appType", record != null && record.getAppType() != null ? record.getAppType().name() : "-");
        ctx.setVariable("host", record != null ? record.getMonitorName() : "-");
        ctx.setVariable("metricGroup", alert.getMetricGroup());
        ctx.setVariable("fieldName", alert.getFieldName());
        ctx.setVariable("currentValue", alert.getCurrentValue());
        ctx.setVariable("unit", value != null ? value.getUnit() : "");
        ctx.setVariable("thresholdDesc", alert.getThresholdDesc());
        ctx.setVariable("hitCount", alert.getHitCount());
        ctx.setVariable("windowMin", rule != null ? rule.getWindowSec() / 60 : 5);
        ctx.setVariable("firstTrigger", fmt(alert.getFirstTriggerAt()));
        ctx.setVariable("groupName", groupName);
        ctx.setVariable("alertNo", alert.getAlertNo());
        ctx.setVariable("detailUrl", linkBuilder.detailUrl(alert.getAlertNo()));
        ctx.setVariable("silenceUrl", linkBuilder.silenceUrl(alert.getAlertNo()));
        try {
            return templateEngine.process("mail/alert-email", ctx);
        } catch (Exception e) {
            log.error("mail render failed, fallback to plain text: {}", e.getMessage());
            return fallbackText(alert, record, groupName, isRecovery);
        }
    }

    private String fmt(OffsetDateTime t) {
        return t == null ? "-" : t.format(FMT);
    }

    private String fallbackText(AlertEntity alert, MetricRecord record, String groupName, boolean isRecovery) {
        return (isRecovery ? "[恢复] " : "[告警] ") + alert.getSeverity().label() + " "
                + (record != null ? record.getMonitorName() : alert.getMonitorId())
                + " " + alert.getMetricGroup() + "." + alert.getFieldName()
                + " 当前值=" + alert.getCurrentValue() + " 阈值=" + alert.getThresholdDesc()
                + " 命中=" + alert.getHitCount() + " 策略组=" + groupName
                + " 详情=" + linkBuilder.detailUrl(alert.getAlertNo());
    }
}
