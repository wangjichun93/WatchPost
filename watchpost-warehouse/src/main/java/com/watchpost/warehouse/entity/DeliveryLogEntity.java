package com.watchpost.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 邮件投递日志 (全量可查 + 失败重试调度)。对应 wp_delivery_log。 */
@Getter
@Setter
@Entity
@Table(name = "wp_delivery_log")
public class DeliveryLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(nullable = false, length = 16)
    private String channel = "EMAIL";

    @Column(nullable = false, length = 128)
    private String recipient;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false)
    private Integer status = 0;   // 0待发 1成功 2失败 3退信

    @Column(name = "retry_no", nullable = false)
    private Integer retryNo = 0;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "error_reason", length = 512)
    private String errorReason;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();
}
