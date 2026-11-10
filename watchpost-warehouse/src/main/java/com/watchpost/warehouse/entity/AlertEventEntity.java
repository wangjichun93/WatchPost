package com.watchpost.warehouse.entity;

import com.watchpost.common.enums.AlertState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 告警状态迁移与确认审计。对应 wp_alert_event。 */
@Getter
@Setter
@Entity
@Table(name = "wp_alert_event")
public class AlertEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state")
    private AlertState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private AlertState toState;

    @Column(length = 64)
    private String operator;

    @Column(length = 512)
    private String note;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();
}
