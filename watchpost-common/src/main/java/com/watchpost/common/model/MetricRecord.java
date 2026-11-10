package com.watchpost.common.model;

import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.ProtocolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一个监控实例一次采集周期的结果聚合。
 * 采集器产出后, 拆分写入实时缓存/时序库, 并整体发布到判定管道。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricRecord {
    private long monitorId;
    private String monitorName;
    private AppType appType;
    private ProtocolType protocol;
    /** 采集时间戳 (epoch millis) */
    private long timestamp;
    /** 本次采集是否成功 (失败时 values 为空, error 有值) */
    private boolean success;
    /** 连续失败计数 (主机可达性规则依赖) */
    private int connFailCount;
    private String error;
    /** 采集耗时 (ms) */
    private long costMs;
    private List<MetricValue> values;
}
