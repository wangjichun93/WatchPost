package com.watchpost.collector.protocol;

import com.watchpost.common.enums.FieldType;
import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.model.CollectTemplate;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.model.MonitorTarget;
import com.watchpost.common.spi.ProtocolCollector;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 采集器 (MySQL/PostgreSQL/Oracle, PRD 05)。
 * 只读账号直连, 只查系统/性能视图; 连接池按实例隔离并自动回收; 查询超时 ≤5s。
 * Oracle 固定 ojdbc8 (兼容 12.1)。单库不可达不拖垮其他任务 (池隔离 + 超时)。
 */
@Slf4j
@Component
public class JdbcCollector implements ProtocolCollector {

    /** 按 monitorId 隔离的连接池 (PRD: 连接池按实例隔离并自动回收)。 */
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.jdbc;
    }

    @Override
    public MetricRecord collect(MonitorTarget target) {
        long t0 = System.currentTimeMillis();
        MetricRecord.MetricRecordBuilder rb = MetricRecord.builder()
                .monitorId(target.getMonitorId()).monitorName(target.getMonitorName())
                .appType(target.getAppType()).protocol(ProtocolType.jdbc).timestamp(t0);
        List<MetricValue> values = new ArrayList<>();
        try {
            HikariDataSource ds = poolFor(target);
            CollectTemplate tpl = target.getTemplate();
            if (tpl != null && tpl.getQueries() != null) {
                try (Connection conn = ds.getConnection()) {
                    for (CollectTemplate.SqlQuery q : tpl.getQueries()) {
                        values.addAll(runQuery(conn, q, target.getTimeoutMs()));
                    }
                }
            }
            return rb.success(true).costMs(System.currentTimeMillis() - t0).values(values).build();
        } catch (Exception e) {
            log.debug("JDBC collect failed monitorId={}: {}", target.getMonitorId(), e.getMessage());
            // 池可能已失效, 回收以便下次重建
            invalidate(target.getMonitorId());
            return rb.success(false).error(e.getMessage())
                    .costMs(System.currentTimeMillis() - t0).values(values).build();
        }
    }

    @Override
    public boolean precheck(MonitorTarget target) {
        try {
            HikariDataSource ds = poolFor(target);
            try (Connection conn = ds.getConnection()) {
                return conn.isValid((int) (target.getTimeoutMs() / 1000));
            }
        } catch (Exception e) {
            log.info("JDBC precheck failed {}:{} -> {}", target.getHost(), target.getPort(), e.getMessage());
            invalidate(target.getMonitorId());
            return false;
        }
    }

    private List<MetricValue> runQuery(Connection conn, CollectTemplate.SqlQuery q, long timeoutMs) {
        List<MetricValue> out = new ArrayList<>();
        boolean filtered = q.getColumnMapping() != null && !q.getColumnMapping().isEmpty();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout((int) Math.max(1, timeoutMs / 1000));   // PRD: 查询超时 ≤5s
            st.setMaxRows(1000);                                        // 只读系统视图, 限制行数
            try (ResultSet rs = st.executeQuery(q.getSql())) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                while (rs.next()) {
                    for (int i = 1; i <= cols; i++) {
                        String col = md.getColumnLabel(i).toLowerCase();
                        // columnMapping 非空时仅保留映射列 (过滤 SHOW SLAVE STATUS 等宽结果)
                        if (filtered && !q.getColumnMapping().containsKey(col)) continue;
                        String field = mapField(q, col);
                        Object v = rs.getObject(i);
                        out.add(toValue(q.getGroup(), field, v));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("query failed group={}: {}", q.getGroup(), e.getMessage());
        }
        return out;
    }

    private String mapField(CollectTemplate.SqlQuery q, String column) {
        if (q.getColumnMapping() != null && q.getColumnMapping().containsKey(column)) {
            return q.getColumnMapping().get(column);
        }
        return column;
    }

    private MetricValue toValue(String group, String field, Object v) {
        if (v instanceof Number n) {
            return MetricValue.builder().metricGroup(group).fieldName(field)
                    .fieldType(FieldType.DOUBLE).doubleValue(n.doubleValue()).build();
        }
        return MetricValue.builder().metricGroup(group).fieldName(field)
                .fieldType(FieldType.STRING).strValue(v == null ? null : String.valueOf(v)).build();
    }

    /** 获取/创建实例隔离连接池。 */
    private HikariDataSource poolFor(MonitorTarget target) {
        return pools.computeIfAbsent(target.getMonitorId(), id -> createPool(target));
    }

    private HikariDataSource createPool(MonitorTarget target) {
        Map<String, String> cred = target.getCredential() == null ? Map.of() : target.getCredential();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(buildJdbcUrl(target));
        cfg.setUsername(cred.get("username"));
        cfg.setPassword(cred.getOrDefault("password", cred.get("secret")));
        cfg.setMaximumPoolSize(3);              // 按实例隔离小池
        cfg.setMinimumIdle(0);                  // 空闲回收
        cfg.setIdleTimeout(30_000);
        cfg.setMaxLifetime(120_000);
        cfg.setConnectionTimeout(target.getTimeoutMs());
        cfg.setValidationTimeout(Math.min(target.getTimeoutMs(), 3000));
        cfg.setReadOnly(true);                  // 只读
        cfg.setPoolName("wp-jdbc-" + target.getMonitorId());
        cfg.setInitializationFailTimeout(-1);   // 创建时不强制连通, 由 precheck/collect 处理
        log.info("created JDBC pool monitorId={} url={}", target.getMonitorId(), cfg.getJdbcUrl());
        return new HikariDataSource(cfg);
    }

    private String buildJdbcUrl(MonitorTarget target) {
        String host = target.getHost();
        int port = target.getPort() != null ? target.getPort() : defaultPort(target.getAppType().name());
        return switch (target.getAppType()) {
            case mysql -> "jdbc:mysql://%s:%d/?useSSL=false&connectTimeout=5000&socketTimeout=5000".formatted(host, port);
            case postgresql -> "jdbc:postgresql://%s:%d/postgres?connectTimeout=5&socketTimeout=5".formatted(host, port);
            case oracle -> "jdbc:oracle:thin:@//%s:%d".formatted(host, port);
            default -> throw new IllegalArgumentException("unsupported jdbc app_type: " + target.getAppType());
        };
    }

    private int defaultPort(String appType) {
        return switch (appType) {
            case "mysql" -> 3306;
            case "postgresql" -> 5432;
            case "oracle" -> 1521;
            default -> 0;
        };
    }

    private void invalidate(long monitorId) {
        HikariDataSource ds = pools.remove(monitorId);
        if (ds != null) {
            try { ds.close(); } catch (Exception ignored) {}
        }
    }

    @PreDestroy
    public void closeAll() {
        pools.values().forEach(ds -> { try { ds.close(); } catch (Exception ignored) {} });
        pools.clear();
    }
}
