package com.watchpost.warehouse.store;

import com.watchpost.common.model.MetricValue;
import com.watchpost.common.spi.TimeSeriesStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.session.Session;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.read.common.Field;
import org.apache.tsfile.read.common.RowRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时序存储 IoTDB 实现 (生产推荐, 可插拔)。
 * 激活: warehouse.store.type=iotdb 且 classpath 存在 org.apache.iotdb.session.Session。
 *
 * 数据模型 (IoTDB 树形):
 *   device     = root.{database}.m{monitorId}.{group}
 *   measurement= {field}
 *   value      = DOUBLE (数值) / TEXT (字符串)
 * TTL 30天由 IoTDB 库级 TTL 策略承载; 降采样用 GROUP BY TIME 聚合查询下推到库侧。
 *
 * 依赖说明: iotdb-session 在 warehouse 为 optional 编译依赖; 运行时需通过
 *   `mvn package -Piotdb` (startup 模块 profile) 或显式加入依赖打入 fat jar。
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.iotdb.session.Session")
@ConditionalOnProperty(name = "warehouse.store.type", havingValue = "iotdb")
public class IotdbTimeSeriesStore implements TimeSeriesStore {

    @Value("${warehouse.iotdb.host:127.0.0.1}")
    private String host;
    @Value("${warehouse.iotdb.port:6667}")
    private int port;
    @Value("${warehouse.iotdb.user:root}")
    private String user;
    @Value("${warehouse.iotdb.password:root}")
    private String password;
    @Value("${warehouse.iotdb.database:watchpost}")
    private String database;

    private volatile Session session;

    /** 懒加载 + 断线重连的会话获取。 */
    private synchronized Session session() throws Exception {
        if (session != null) return session;
        Session s = new Session.Builder()
                .host(host).port(port)
                .username(user).password(password)
                .build();
        s.open();
        this.session = s;
        log.info("IoTDB session opened: {}:{} db={}", host, port, database);
        return s;
    }

    @Override
    public boolean isAvailable() {
        try {
            session().executeQueryStatement("SHOW VERSION");
            return true;
        } catch (Exception e) {
            log.debug("IoTDB unavailable: {}", e.getMessage());
            closeQuietly();
            return false;
        }
    }

    @Override
    public void write(long monitorId, String app, List<MetricValue> values, long timestamp) {
        if (values == null || values.isEmpty()) return;
        // 按 group 分设备, 同设备多字段一次 insertRecord
        Map<String, List<MetricValue>> byGroup = new LinkedHashMap<>();
        for (MetricValue v : values) {
            byGroup.computeIfAbsent(v.getMetricGroup(), k -> new ArrayList<>()).add(v);
        }
        try {
            Session s = session();
            for (Map.Entry<String, List<MetricValue>> e : byGroup.entrySet()) {
                String device = device(monitorId, e.getKey());
                List<String> measurements = new ArrayList<>();
                List<TSDataType> types = new ArrayList<>();
                List<Object> vals = new ArrayList<>();
                for (MetricValue v : e.getValue()) {
                    measurements.add(sanitize(v.getFieldName()));
                    if (v.isNumeric() && v.getDoubleValue() != null) {
                        types.add(TSDataType.DOUBLE);
                        vals.add(v.getDoubleValue());
                    } else if (v.getStrValue() != null) {
                        types.add(TSDataType.TEXT);
                        vals.add(v.getStrValue());
                    }
                }
                if (!measurements.isEmpty()) {
                    s.insertRecord(device, timestamp, measurements, types, vals);
                }
            }
        } catch (Exception ex) {
            log.warn("IoTDB write failed monitorId={}: {}", monitorId, ex.getMessage());
            closeQuietly();   // 触发下次重连
        }
    }

    @Override
    public Map<String, List<Point>> queryRange(long monitorId, String group, String field, long fromMs, long toMs) {
        String device = device(monitorId, group);
        String m = sanitize(field);
        String sql = String.format("SELECT %s FROM %s WHERE time >= %d AND time <= %d", m, device, fromMs, toMs);
        Map<String, List<Point>> out = new LinkedHashMap<>();
        List<Point> pts = new ArrayList<>();
        try {
            SessionDataSet ds = session().executeQueryStatement(sql);
            try {
                while (ds.hasNext()) {
                    RowRecord r = ds.next();
                    Field f = firstField(r);
                    if (f != null && !isNull(f)) {
                        pts.add(Point.of(r.getTimestamp(), numeric(f)));
                    }
                }
            } finally {
                ds.closeOperationHandle();
            }
        } catch (Exception e) {
            log.debug("IoTDB queryRange failed: {}", e.getMessage());
            closeQuietly();
        }
        out.put(group + "#" + field, pts);
        return out;
    }

    @Override
    public Map<String, List<Point>> queryInterval(long monitorId, String group, String field,
                                                  long fromMs, long toMs, long stepSec) {
        String device = device(monitorId, group);
        String m = sanitize(field);
        long stepMs = Math.max(stepSec, 60) * 1000;
        // 聚合下推到库侧: GROUP BY TIME 分桶 avg/min/max
        String sql = String.format(
                "SELECT avg(%s), min(%s), max(%s) FROM %s GROUP BY TIME([%d, %d), %dms)",
                m, m, m, device, fromMs, toMs, stepMs);
        Map<String, List<Point>> out = new LinkedHashMap<>();
        List<Point> pts = new ArrayList<>();
        try {
            SessionDataSet ds = session().executeQueryStatement(sql);
            try {
                while (ds.hasNext()) {
                    RowRecord r = ds.next();
                    List<Field> fs = r.getFields();
                    // SELECT 顺序: avg, min, max
                    Double avg = fs.size() > 0 ? safeNum(fs.get(0)) : null;
                    Double min = fs.size() > 1 ? safeNum(fs.get(1)) : null;
                    Double max = fs.size() > 2 ? safeNum(fs.get(2)) : null;
                    if (avg != null || min != null || max != null) {
                        pts.add(new Point(r.getTimestamp(), min, max, avg, avg));
                    }
                }
            } finally {
                ds.closeOperationHandle();
            }
        } catch (Exception e) {
            log.debug("IoTDB queryInterval failed: {}", e.getMessage());
            closeQuietly();
        }
        out.put(group + "#" + field, pts);
        return out;
    }

    private String device(long monitorId, String group) {
        return "root." + sanitize(database) + ".m" + monitorId + "." + sanitize(group);
    }

    /** IoTDB 路径段仅允许 [a-zA-Z0-9_], 数字开头需前缀。 */
    private String sanitize(String s) {
        if (s == null || s.isEmpty()) return "_";
        String cleaned = s.replaceAll("[^a-zA-Z0-9_]", "_");
        return Character.isDigit(cleaned.charAt(0)) ? "_" + cleaned : cleaned;
    }

    private Field firstField(RowRecord r) {
        List<Field> fs = r.getFields();
        return fs == null || fs.isEmpty() ? null : fs.get(0);
    }

    private boolean isNull(Field f) {
        return f == null || f.getDataType() == null;
    }

    private double numeric(Field f) {
        return switch (f.getDataType()) {
            case DOUBLE -> f.getDoubleV();
            case FLOAT -> f.getFloatV();
            case INT64 -> f.getLongV();
            case INT32 -> f.getIntV();
            case BOOLEAN -> f.getBoolV() ? 1d : 0d;
            default -> 0d;
        };
    }

    private Double safeNum(Field f) {
        return isNull(f) ? null : numeric(f);
    }

    private synchronized void closeQuietly() {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
            session = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        closeQuietly();
        log.info("IoTDB session closed");
    }
}
