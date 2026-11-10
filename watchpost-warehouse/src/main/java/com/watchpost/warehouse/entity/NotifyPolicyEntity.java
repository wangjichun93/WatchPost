package com.watchpost.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** 通知策略: 告警级别 × 对象标签 → 收件人组。对应 wp_notify_policy。 */
@Getter
@Setter
@Entity
@Table(name = "wp_notify_policy")
public class NotifyPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "match_tags_json", columnDefinition = "jsonb")
    private String matchTagsJson;

    /** 匹配的告警级别, 逗号分隔 (如 "CRITICAL,FATAL"); 空表示匹配全部。 */
    @Column(name = "match_severity", length = 64)
    private String matchSeverity;

    public List<String> severityList() {
        if (matchSeverity == null || matchSeverity.isBlank()) return List.of();
        return Arrays.stream(matchSeverity.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Column(name = "notify_group_id", nullable = false)
    private Long notifyGroupId;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime = OffsetDateTime.now();
}
