package com.watchpost.common.spi;

import com.watchpost.common.model.MetricValue;

import java.util.List;
import java.util.Map;

/**
 * 历史时序存储 SPI (可插拔)。默认 Apache IoTDB; 单机起步可用 PostgreSQL 实现; 预留 VM/TDengine/TimescaleDB。
 * 数据模型: (monitorId, app, metricGroup, fieldName, ts, value, labels)。
 */
public interface TimeSeriesStore {

    /** 后端是否可用 (守护所有读写)。 */
    boolean isAvailable();

    /** 批量写入时序点。 */
    void write(long monitorId, String app, List<MetricValue> values, long timestamp);

    /** 原始区间数据 (key: group#field)。 */
    Map<String, List<Point>> queryRange(long monitorId, String group, String field, long fromMs, long toMs);

    /** 聚合(min/max/avg)分桶数据, 用于趋势图降采样。 */
    Map<String, List<Point>> queryInterval(long monitorId, String group, String field,
                                           long fromMs, long toMs, long stepSec);

    /** 一个时序点。 */
    record Point(long ts, Double min, Double max, Double avg, Double value) {
        public static Point of(long ts, double value) { return new Point(ts, value, value, value, value); }
    }
}
