package com.watchpost.warehouse.entity;

import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.ProtocolType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 监控对象实例。对应 wp_monitor。 */
@Getter
@Setter
@Entity
@Table(name = "wp_monitor")
public class MonitorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", nullable = false)
    private AppType appType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProtocolType protocol;

    @Column(nullable = false, length = 128)
    private String host;

    @Column
    private Integer port;

    @Column(name = "interval_sec", nullable = false)
    private Integer intervalSec = 30;

    @Column(name = "template_id")
    private Long templateId;

    @Column(nullable = false)
    private Integer status = 1;   // 0停用 1正常 2不可达

    @Column(name = "conn_fail_cnt", nullable = false)
    private Integer connFailCount = 0;

    @Column(name = "last_collect_at")
    private OffsetDateTime lastCollectAt;

    @Column(name = "last_value_json", columnDefinition = "jsonb")
    private String lastValueJson;

    /** 扩展配置 JSON (HTTP 型: method/path/headers/body/expectStatus; 其他预留)。对应 V3 extra_json。 */
    @Column(name = "extra_json", columnDefinition = "jsonb")
    private String extraJson;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "create_by")
    private Long createBy;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime = OffsetDateTime.now();
}
