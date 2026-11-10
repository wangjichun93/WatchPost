package com.watchpost.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 采集器节点注册 (v1 单机一条; 集群期水平扩展, 调度分片依据)。对应 wp_collector_node。 */
@Getter
@Setter
@Entity
@Table(name = "wp_collector_node")
public class CollectorNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String identity;

    @Column(nullable = false, length = 16)
    private String mode = "public";

    @Column(length = 128)
    private String host;

    @Column
    private Integer port;

    @Column(nullable = false)
    private Integer status = 1;   // 0离线 1在线

    @Column(name = "instance_cnt", nullable = false)
    private Integer instanceCnt = 0;

    @Column(name = "last_heartbeat")
    private OffsetDateTime lastHeartbeat;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();
}
