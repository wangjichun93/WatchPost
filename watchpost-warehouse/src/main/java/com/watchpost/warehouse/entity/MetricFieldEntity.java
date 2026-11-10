package com.watchpost.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 指标字段 (一等公民, 每个字段可独立绑定阈值规则)。对应 wp_metric_field。 */
@Getter
@Setter
@Entity
@Table(name = "wp_metric_field",
        uniqueConstraints = @UniqueConstraint(name = "uk_field", columnNames = {"monitor_id", "metric_group", "field_name"}))
public class MetricFieldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "metric_group", nullable = false, length = 64)
    private String metricGroup;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "field_type", nullable = false, length = 16)
    private String fieldType = "double";

    @Column(length = 16)
    private String unit;

    @Column(name = "label_flag", nullable = false)
    private Boolean labelFlag = false;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    public String key() { return metricGroup + "#" + fieldName; }
}
