package com.watchpost.common.model;

import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.ProtocolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 采集模板 (YAML 反序列化产物)。对应 docs/templates/*.yaml 与 wp_monitor_template.yaml_spec。
 * 一个模板声明: 协议 + 采集命令/SQL + 字段映射 + 单位换算 + 断言。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectTemplate {
    private AppType app;
    private ProtocolType protocol;
    /** 默认采集周期 (秒), 实例可覆写 */
    private int collectInterval;
    /** 采集超时 (秒), PRD ≤5s */
    private int timeout;
    /** 认证方式: password/key/community/token 等 (凭据值运行时从 MonitorTarget.credential 注入) */
    private Map<String, Object> auth;

    // ---- SSH / 命令型 ----
    private List<MetricGroup> metrics;

    // ---- HTTP 型 ----
    private HttpRequest request;
    private HttpChecks checks;
    private List<String> timingPhases;

    // ---- JDBC 型 ----
    private List<SqlQuery> queries;

    /** 一组指标 (如 cpu/memory/disk), 含采集命令与字段定义。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MetricGroup {
        private String group;
        /** 采集命令 (SSH) */
        private String command;
        /** 解析器: regex/awk/table/json/line */
        private String parser;
        /** table 解析按某列分组 (如 disk 的 mountpoint) */
        private String keyBy;
        private List<FieldDef> fields;
    }

    /** 单个字段定义 + 单位换算表达式。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FieldDef {
        private String name;
        private String type;     // double/long/int/string/bool
        private String unit;
        /** 由解析变量计算的表达式 (JEXL), 如 "100 - idle" */
        private String expr;
        /** table 解析取第几列 (1-based) */
        private Integer column;
        /** 是否作为标签维度 */
        private boolean label;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HttpRequest {
        private String method;
        private String url;
        private Map<String, String> headers;
        private String body;
        private List<Integer> expectStatus;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HttpChecks {
        private boolean sslCertDays;
        private List<JsonAssert> jsonPathAssert;
        private String bodyKeyword;
        private Double baselineSizeRatio;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class JsonAssert {
        private String path;
        private Object expect;
    }

    /** JDBC 只读查询 (仅系统/性能视图)。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SqlQuery {
        private String group;
        private String sql;
        /** 结果列 → 字段名映射 */
        private Map<String, String> columnMapping;
        private List<FieldDef> fields;
    }
}
