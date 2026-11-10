package com.watchpost.collector.schedule;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 采集器配置 (collector.*)。
 * 周期调度、虚拟线程、协议舱壁并发上限、采集超时等。
 */
@Data
@Component
@ConfigurationProperties(prefix = "collector")
public class CollectorProperties {

    /** 是否启用采集调度 (v1 单机 true; 集群期 manager 可置 false 仅做协调)。 */
    private boolean enabled = true;

    /** 默认采集超时 (ms), PRD ≤5s。实例级可覆写。 */
    private long defaultTimeoutMs = 5000;

    /** 调度线程数 (时间轮/周期触发)。 */
    private int schedulerThreads = 2;

    /** 默认协议舱壁并发上限。 */
    private int defaultBulkhead = 32;

    /** 各协议舱壁并发上限 (collector.bulkhead.ssh=16 等)。 */
    private Map<String, Integer> bulkhead = new HashMap<>();

    /** 采集失败连续多少次标记实例不可达。 */
    private int unreachableThreshold = 2;

    /** 虚拟线程执行器名称前缀。 */
    private String virtualThreadPrefix = "collect-";
}
