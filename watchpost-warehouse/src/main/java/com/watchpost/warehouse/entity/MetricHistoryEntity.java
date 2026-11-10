package com.watchpost.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 时序历史 (v1 默认 PostgreSQL 落盘, 按月分区)。对应 wp_metric_history。
 * 说明: 架构默认 TSDB 为 Apache IoTDB (可插拔); 此实体为单机起步/降级方案,
 * 切换 IoTDB 后由 IotdbTimeSeriesStore 承载, 此表停用。
 * 注意: 分区表主键含 record_time, 故用复合主键。
 */
@Getter
@Setter
@Entity
@Table(name = "wp_metric_history")
@IdClass(MetricHistoryEntity.PK.class)
public class MetricHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    @Column(name = "app_type", nullable = false, length = 16)
    private String appType;

    @Column(name = "metric_group", nullable = false, length = 64)
    private String metricGroup;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "field_type", nullable = false)
    private Integer fieldType = 0;

    @Column(name = "dbl_value")
    private Double dblValue;

    @Column(name = "str_value", length = 255)
    private String strValue;

    @Column(name = "labels_json", columnDefinition = "jsonb")
    private String labelsJson;

    @Id
    @Column(name = "record_time", nullable = false)
    private Long recordTime;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    /** 复合主键 (id, record_time) — 分区表要求分区键入主键。 */
    @Getter
    @Setter
    public static class PK implements java.io.Serializable {
        private Long id;
        private Long recordTime;
        public PK() {}
        public PK(Long id, Long recordTime) { this.id = id; this.recordTime = recordTime; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return java.util.Objects.equals(id, pk.id) && java.util.Objects.equals(recordTime, pk.recordTime);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, recordTime); }
    }
}
