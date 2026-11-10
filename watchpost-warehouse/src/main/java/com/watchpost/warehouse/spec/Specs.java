package com.watchpost.warehouse.spec;

import com.watchpost.common.enums.AlertState;
import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.Severity;
import com.watchpost.warehouse.entity.AlertEntity;
import com.watchpost.warehouse.entity.MonitorEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态查询 Specification 构造器 (Criteria API)。
 *
 * 为什么用 Specification 而非 @Query:
 * JDBC url 设了 stringtype=unspecified (原生 enum/jsonb 列写入所需), 导致 String 参数以 unknown 类型发送。
 * 在 `lower(concat('%', :kw, '%'))` 中 PG 无法推断 concat 参数类型 → 解析为 bytea → `lower(bytea)` 报错;
 * `:param IS NULL` 的 untyped-null 参数也会报 "could not determine data type of parameter"。
 * Specification 在 Java 侧条件化构造谓词: null 过滤直接省略 (无 untyped-null 参数),
 * LIKE 模式在 Java 拼好后直接用于 `like` (PG 从 LIKE 右操作数推断为 text, 无 concat), 彻底规避。
 */
public final class Specs {

    private Specs() {}

    /** 监控对象搜索: deleted=false + 可选 appType/status/keyword(name|host 模糊)。 */
    public static Specification<MonitorEntity> monitors(AppType appType, Integer status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (appType != null) {
                ps.add(cb.equal(root.get("appType"), appType));
            }
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                // 模式在 Java 侧拼好并小写化; like 的右操作数让 PG 推断为 text (无 concat/bytea 问题)
                String pattern = "%" + keyword.toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("host")), pattern)
                ));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** 告警搜索: 可选 state/monitorId/severity。 */
    public static Specification<AlertEntity> alerts(AlertState state, Long monitorId, Severity severity) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (state != null) {
                ps.add(cb.equal(root.get("state"), state));
            }
            if (monitorId != null) {
                ps.add(cb.equal(root.get("monitorId"), monitorId));
            }
            if (severity != null) {
                ps.add(cb.equal(root.get("severity"), severity));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
