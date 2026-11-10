package com.watchpost.warehouse.entity;

import com.watchpost.common.enums.CompareOp;
import com.watchpost.common.enums.EffectivePeriod;
import com.watchpost.common.enums.RuleScope;
import com.watchpost.common.enums.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 阈值规则 (逐字段可配, 优先级覆写)。对应 wp_threshold_rule。 */
@Getter
@Setter
@Entity
@Table(name = "wp_threshold_rule")
public class ThresholdRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleScope scope;

    @Column(name = "scope_ref", length = 64)
    private String scopeRef;

    @Column(name = "monitor_id")
    private Long monitorId;

    @Column(name = "metric_field_id")
    private Long metricFieldId;

    @Column(name = "metric_group", length = 64)
    private String metricGroup;

    @Column(name = "field_name", length = 64)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompareOp operator;

    @Column(name = "threshold_json", nullable = false, columnDefinition = "jsonb")
    private String thresholdJson;

    @Column(name = "consecutive_n", nullable = false)
    private Integer consecutiveN = 3;

    @Column(name = "window_sec", nullable = false)
    private Integer windowSec = 300;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity = Severity.WARN;

    @Enumerated(EnumType.STRING)
    @Column(name = "effective_period", nullable = false)
    private EffectivePeriod effectivePeriod = EffectivePeriod.ALL_DAY;

    @Column(name = "cron_expr", length = 64)
    private String cronExpr;

    @Column(name = "suppress_sec", nullable = false)
    private Integer suppressSec = 600;

    @Column(name = "recovery_notify", nullable = false)
    private Boolean recoveryNotify = true;

    @Column(name = "notify_policy_id")
    private Long notifyPolicyId;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime = OffsetDateTime.now();
}
