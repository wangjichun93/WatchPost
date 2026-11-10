package com.watchpost.warehouse.entity;

import com.watchpost.common.enums.AlertState;
import com.watchpost.common.enums.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 告警主体 (活动+历史)。对应 wp_alert。同 fingerprint 重复命中合并为一条, 仅递增 hit_count。 */
@Getter
@Setter
@Entity
@Table(name = "wp_alert")
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_no", nullable = false, unique = true, length = 32)
    private String alertNo;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    @Column(name = "metric_field_id")
    private Long metricFieldId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "metric_group", length = 64)
    private String metricGroup;

    @Column(name = "field_name", length = 64)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertState state = AlertState.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "current_value", length = 64)
    private String currentValue;

    @Column(name = "threshold_desc", length = 128)
    private String thresholdDesc;

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount = 1;

    @Column(name = "first_trigger_at")
    private OffsetDateTime firstTriggerAt;

    @Column(name = "last_trigger_at")
    private OffsetDateTime lastTriggerAt;

    @Column(name = "ack_by")
    private Long ackBy;

    @Column(name = "ack_at")
    private OffsetDateTime ackAt;

    @Column(name = "recovered_at")
    private OffsetDateTime recoveredAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "notify_group_id")
    private Long notifyGroupId;

    @Column(name = "silenced_until")
    private OffsetDateTime silencedUntil;

    @Column(nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime = OffsetDateTime.now();

    public boolean isSilenced() {
        return silencedUntil != null && silencedUntil.isAfter(OffsetDateTime.now());
    }
}
