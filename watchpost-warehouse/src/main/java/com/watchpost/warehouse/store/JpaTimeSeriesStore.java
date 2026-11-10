package com.watchpost.warehouse.store;

import com.watchpost.common.model.MetricValue;
import com.watchpost.common.spi.TimeSeriesStore;
import com.watchpost.common.util.JsonUtil;
import com.watchpost.warehouse.entity.MetricHistoryEntity;
import com.watchpost.warehouse.repository.MetricHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时序存储默认实现: PostgreSQL 落盘 (wp_metric_history, 按月分区, TTL 30天由分区滚动清理)。
 * 单机起步/降级方案; 生产建议切换 IoTDB (IotdbTimeSeriesStore), 通过 warehouse.store.type 选择。
 * 激活条件: warehouse.store.type=jpa (默认)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "warehouse.store.type", havingValue = "jpa", matchIfMissing = true)
public class JpaTimeSeriesStore implements TimeSeriesStore {

    private final MetricHistoryRepository repo;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void write(long monitorId, String app, List<MetricValue> values, long timestamp) {
        if (values == null || values.isEmpty()) return;
        List<MetricHistoryEntity> batch = new ArrayList<>(values.size());
        for (MetricValue v : values) {
            MetricHistoryEntity e = new MetricHistoryEntity();
            e.setMonitorId(monitorId);
            e.setAppType(app);
            e.setMetricGroup(v.getMetricGroup());
            e.setFieldName(v.getFieldName());
            e.setFieldType(v.getFieldType() == null ? 0 : v.getFieldType().tsdbType());
            if (v.isNumeric()) {
                e.setDblValue(v.getDoubleValue());
            } else {
                e.setStrValue(v.getStrValue());
            }
            if (v.getLabels() != null && !v.getLabels().isEmpty()) {
                e.setLabelsJson(JsonUtil.toJson(v.getLabels()));
            }
            e.setRecordTime(timestamp);
            batch.add(e);
        }
        repo.saveAll(batch);
    }

    @Override
    public Map<String, List<Point>> queryRange(long monitorId, String group, String field, long fromMs, long toMs) {
        List<MetricHistoryEntity> rows = repo.queryRange(monitorId, group, field, fromMs, toMs);
        Map<String, List<Point>> out = new LinkedHashMap<>();
        List<Point> pts = new ArrayList<>(rows.size());
        for (MetricHistoryEntity r : rows) {
            pts.add(Point.of(r.getRecordTime(), r.getDblValue() == null ? 0d : r.getDblValue()));
        }
        out.put(group + "#" + field, pts);
        return out;
    }

    @Override
    public Map<String, List<Point>> queryInterval(long monitorId, String group, String field,
                                                  long fromMs, long toMs, long stepSec) {
        // 单机默认实现: 取原始区间后按 step 分桶聚合 (min/max/avg)。
        // 生产 IoTDB 实现下推聚合到库侧, 效率更高。
        List<MetricHistoryEntity> rows = repo.queryRange(monitorId, group, field, fromMs, toMs);
        long stepMs = Math.max(stepSec, 60) * 1000;
        Map<Long, double[]> buckets = new LinkedHashMap<>();  // ts -> [min,max,sum,cnt]
        for (MetricHistoryEntity r : rows) {
            if (r.getDblValue() == null) continue;
            long bucket = (r.getRecordTime() / stepMs) * stepMs;
            double[] agg = buckets.computeIfAbsent(bucket, k -> new double[]{Double.MAX_VALUE, Double.MIN_VALUE, 0, 0});
            double val = r.getDblValue();
            agg[0] = Math.min(agg[0], val);
            agg[1] = Math.max(agg[1], val);
            agg[2] += val;
            agg[3] += 1;
        }
        List<Point> pts = new ArrayList<>(buckets.size());
        buckets.forEach((ts, agg) -> pts.add(new Point(ts, agg[0], agg[1], agg[2] / agg[3], agg[2] / agg[3])));
        Map<String, List<Point>> out = new LinkedHashMap<>();
        out.put(group + "#" + field, pts);
        return out;
    }
}
