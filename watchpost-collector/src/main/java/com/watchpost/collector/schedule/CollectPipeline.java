package com.watchpost.collector.schedule;

import com.watchpost.common.enums.MonitorStatus;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MonitorTarget;
import com.watchpost.common.spi.MetricQueue;
import com.watchpost.common.spi.ProtocolCollector;
import com.watchpost.common.spi.RealtimeStore;
import com.watchpost.common.spi.TimeSeriesStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 采集管道: 单实例单周期的完整链路。
 * 协议采集 → 标准化结果 → 双写(实时缓存 + 时序库) → 发布判定管道 → 健康态更新。
 * 在虚拟线程上执行, 由 CollectScheduler 派发; 舱壁许可由调度层持有。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectPipeline {

    private final CollectorRegistry registry;
    private final RealtimeStore realtimeStore;
    private final TimeSeriesStore timeSeriesStore;
    private final MetricQueue metricQueue;
    private final CollectorProperties props;

    /**
     * 执行一次采集。永不抛出 (异常收敛进 MetricRecord), 保证不阻塞调度。
     */
    public void run(MonitorTarget target) {
        ProtocolCollector collector;
        try {
            collector = registry.get(target.getProtocol());
        } catch (Exception e) {
            log.error("no collector for monitorId={} protocol={}", target.getMonitorId(), target.getProtocol());
            return;
        }

        MetricRecord record;
        try {
            record = collector.collect(target);
        } catch (Exception e) {
            // 兜底: 采集器未自行捕获的异常
            record = MetricRecord.builder()
                    .monitorId(target.getMonitorId()).monitorName(target.getMonitorName())
                    .appType(target.getAppType()).protocol(target.getProtocol())
                    .timestamp(System.currentTimeMillis()).success(false).error(e.getMessage())
                    .values(java.util.List.of()).build();
        }

        // 1. 实时值缓存 (无论成败都更新健康态)
        try {
            realtimeStore.putLatest(target.getMonitorId(), record.getValues(), record.getTimestamp(),
                    record.isSuccess(), record.getConnFailCount());
        } catch (Exception e) {
            log.warn("realtime store failed monitorId={}: {}", target.getMonitorId(), e.getMessage());
        }

        // 2. 时序库落盘 (仅成功且有值)
        if (record.isSuccess() && record.getValues() != null && !record.getValues().isEmpty()) {
            try {
                timeSeriesStore.write(target.getMonitorId(), target.getAppType().name(),
                        record.getValues(), record.getTimestamp());
            } catch (Exception e) {
                log.warn("tsdb write failed monitorId={}: {}", target.getMonitorId(), e.getMessage());
            }
        }

        // 3. 健康态更新 (连续失败计数 → 不可达)
        int failCount = record.isSuccess() ? 0 : record.getConnFailCount() + 1;
        int status = failCount >= props.getUnreachableThreshold()
                ? MonitorStatus.UNREACHABLE.code() : MonitorStatus.NORMAL.code();
        try {
            realtimeStore.updateHealth(target.getMonitorId(), status, failCount);
        } catch (Exception e) {
            log.debug("health update failed monitorId={}", target.getMonitorId());
        }
        record.setConnFailCount(failCount);

        // 4. 发布到判定管道 (异步告警)
        try {
            metricQueue.publish(record);
        } catch (Exception e) {
            log.warn("metric queue publish failed monitorId={}: {}", target.getMonitorId(), e.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("collected monitorId={} success={} values={} cost={}ms",
                    target.getMonitorId(), record.isSuccess(),
                    record.getValues() == null ? 0 : record.getValues().size(), record.getCostMs());
        }
    }
}
