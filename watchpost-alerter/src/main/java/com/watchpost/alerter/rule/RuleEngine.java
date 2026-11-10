package com.watchpost.alerter.rule;

import com.watchpost.common.enums.CompareOp;
import com.watchpost.common.enums.RuleScope;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.spi.RuleExpression;
import com.watchpost.common.util.JsonUtil;
import com.watchpost.warehouse.entity.ThresholdRuleEntity;
import com.watchpost.warehouse.repository.ThresholdRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阈值规则引擎 (PRD 04)。
 * 职责: 加载规则并按优先级 (实例 > 类型 > 全局) 选取生效规则; 用 JEXL 求值阈值表达式。
 * 规则缓存: 启动加载 + reload() 热更新 (配置保存即生效, 无需重启)。
 * 判定只读最新值 + 滑动窗口, 不做全量历史回扫 (连续 N 次计数由 RealtimeStore 承载)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngine {

    private final ThresholdRuleRepository ruleRepo;
    private final RuleExpression expression;

    /** app_type -> 全局默认规则 (按字段索引)。 */
    private final AtomicReference<Map<String, List<ThresholdRuleEntity>>> globalRules =
            new AtomicReference<>(new ConcurrentHashMap<>());

    @PostConstruct
    public void init() {
        reload();
    }

    /** 热加载全部启用规则。 */
    public synchronized void reload() {
        Map<String, List<ThresholdRuleEntity>> byType = new ConcurrentHashMap<>();
        for (ThresholdRuleEntity r : ruleRepo.findByEnabledTrue()) {
            if (r.getScope() == RuleScope.GLOBAL && r.getScopeRef() != null) {
                byType.computeIfAbsent(r.getScopeRef(), k -> new ArrayList<>()).add(r);
            }
        }
        // 每类内按优先级降序 (上层覆写下层)
        byType.values().forEach(list -> list.sort(Comparator.comparingInt(ThresholdRuleEntity::getPriority).reversed()));
        globalRules.set(byType);
        log.info("rule engine loaded global templates for {} app_types", byType.size());
    }

    /**
     * 选取某字段生效的规则集合 (实例级覆写 > 类型级全局)。
     * @param monitorId 实例 ID (用于查实例级覆写)
     * @param appType   类型 (用于查全局默认)
     * @param group     指标组
     * @param field     字段名
     */
    public List<ThresholdRuleEntity> resolveRules(long monitorId, String appType, String group, String field) {
        List<ThresholdRuleEntity> result = new ArrayList<>();
        // 实例级覆写 (含字段级)
        for (ThresholdRuleEntity r : ruleRepo.findByMonitorIdAndEnabledTrue(monitorId)) {
            if (matches(r, group, field)) result.add(r);
        }
        // 类型级全局默认
        List<ThresholdRuleEntity> globals = globalRules.get().getOrDefault(appType, List.of());
        for (ThresholdRuleEntity r : globals) {
            if (matches(r, group, field)) result.add(r);
        }
        // 按优先级降序: 实例(20/30) > 类型(10) > 全局(0)
        result.sort(Comparator.comparingInt((ThresholdRuleEntity r) -> r.getScope().priority())
                .thenComparingInt(ThresholdRuleEntity::getPriority).reversed());
        return result;
    }

    private boolean matches(ThresholdRuleEntity r, String group, String field) {
        boolean groupOk = r.getMetricGroup() == null || r.getMetricGroup().equals(group);
        boolean fieldOk = r.getFieldName() == null || r.getFieldName().equals(field);
        return groupOk && fieldOk;
    }

    /**
     * 求值单条规则是否命中 (越限)。
     * @param rule  规则
     * @param value 当前采集值
     * @param baseline 环比基线 (DELTA_PCT 用, 可空)
     * @return true 命中越限
     */
    public boolean evaluate(ThresholdRuleEntity rule, MetricValue value, Double baseline) {
        if (value == null || !value.isNumeric() || value.getDoubleValue() == null) return false;
        Map<String, Object> th = JsonUtil.toMap(rule.getThresholdJson());
        String expr = buildExpression(rule.getOperator(), th, baseline);
        if (expr == null) return false;
        Map<String, Object> bindings = new java.util.HashMap<>();
        bindings.put("value", value.getDoubleValue());
        if (baseline != null) bindings.put("baseline", baseline);
        try {
            return expression.evalBoolean(expr, bindings);
        } catch (Exception e) {
            log.debug("rule eval failed ruleId={} expr={}: {}", rule.getId(), expr, e.getMessage());
            return false;
        }
    }

    /** 由 operator + threshold_json 构造 JEXL 布尔表达式。 */
    private String buildExpression(CompareOp op, Map<String, Object> th, Double baseline) {
        Object v = th.get("value");
        switch (op) {
            case GT: return num(v) != null ? "value > " + num(v) : null;
            case GE: return num(v) != null ? "value >= " + num(v) : null;
            case LT: return num(v) != null ? "value < " + num(v) : null;
            case LE: return num(v) != null ? "value <= " + num(v) : null;
            case EQ: return num(v) != null ? "value == " + num(v) : null;
            case NE: return num(v) != null ? "value != " + num(v) : null;
            case BETWEEN: {
                Object min = th.get("min"), max = th.get("max");
                return (min != null && max != null) ? "value >= " + min + " && value <= " + max : null;
            }
            case DELTA_PCT: {
                // 环比: |value-baseline|/baseline*100 >= pct, direction 决定升/降
                Object pct = th.get("pct");
                if (pct == null || baseline == null || baseline == 0) return null;
                String dir = String.valueOf(th.getOrDefault("direction", "drop"));
                String cmp = "drop".equals(dir)
                        ? "(baseline - value) / baseline * 100 >= " + pct   // 突降
                        : "(value - baseline) / baseline * 100 >= " + pct;  // 突增
                return cmp;
            }
            default: return null;
        }
    }

    private Double num(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    /** 阈值描述 (告警正文用, 如 "≥ 30 s")。 */
    public String describeThreshold(ThresholdRuleEntity rule) {
        Map<String, Object> th = JsonUtil.toMap(rule.getThresholdJson());
        Object v = th.get("value");
        return rule.getOperator().symbol() + " " + (v != null ? v : th);
    }
}
