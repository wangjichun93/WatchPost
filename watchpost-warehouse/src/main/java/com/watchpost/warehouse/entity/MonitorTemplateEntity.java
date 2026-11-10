package com.watchpost.warehouse.entity;

import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.ProtocolType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 采集模板 (按 app_type 内置, 存 YAML 规范, 热加载)。对应 wp_monitor_template。 */
@Getter
@Setter
@Entity
@Table(name = "wp_monitor_template")
public class MonitorTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", nullable = false, unique = true)
    private AppType appType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProtocolType protocol;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "yaml_spec", nullable = false, columnDefinition = "text")
    private String yamlSpec;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private Boolean builtin = true;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime = OffsetDateTime.now();
}
