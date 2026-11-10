package com.watchpost.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 登录与配置变更全量审计 (详情脱敏)。对应 wp_audit_log。 */
@Getter
@Setter
@Entity
@Table(name = "wp_audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64)
    private String username;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(nullable = false)
    private Boolean success = true;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();
}
