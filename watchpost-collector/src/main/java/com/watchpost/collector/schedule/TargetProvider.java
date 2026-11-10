package com.watchpost.collector.schedule;

import com.watchpost.common.model.MonitorTarget;

import java.util.List;
import java.util.Optional;

/**
 * 监控目标提供者 SPI: 向调度器供给「已组装、凭据已解密」的运行时采集目标。
 * 默认实现从 warehouse (wp_monitor + wp_credential + 模板) 组装;
 * 集群期可替换为从 manager 拉取分片目标的实现。
 */
public interface TargetProvider {

    /** 加载全部启用的采集目标 (调度器 refresh 时调用)。 */
    List<MonitorTarget> loadEnabled();

    /** 加载单个目标 (新增/编辑后立即调度用)。 */
    Optional<MonitorTarget> load(long monitorId);
}
