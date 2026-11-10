package com.watchpost.common.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则表达式引擎测试 (PRD 04: 比较符 > ≥ < ≤ == != / between / 环比 Δ%)。
 * 验证阈值判定与字段单位换算的 JEXL 求值正确性。
 */
class JexlRuleExpressionTest {

    private final JexlRuleExpression engine = new JexlRuleExpression();

    @Test
    @DisplayName("比较符判定: CPU ≥ 85% 命中")
    void comparisonOperators() {
        assertTrue(engine.evalBoolean("value >= 85", Map.of("value", 90.0)));
        assertFalse(engine.evalBoolean("value >= 85", Map.of("value", 80.0)));
        assertTrue(engine.evalBoolean("value > 8000", Map.of("value", 8500.0)));
        assertTrue(engine.evalBoolean("value < 95", Map.of("value", 90.0)));   // 命中率 < 95% 告警
        assertTrue(engine.evalBoolean("value == 0", Map.of("value", 0.0)));     // 进程存活数 = 0
        assertTrue(engine.evalBoolean("value != 0", Map.of("value", 1.0)));
    }

    @Test
    @DisplayName("between 区间判定: SSL 证书剩余天数")
    void betweenExpression() {
        // 14 >= days >= 3 (警告区间)
        assertTrue(engine.evalBoolean("value >= 3 && value <= 14", Map.of("value", 10.0)));
        assertFalse(engine.evalBoolean("value >= 3 && value <= 14", Map.of("value", 20.0)));
        assertFalse(engine.evalBoolean("value >= 3 && value <= 14", Map.of("value", 1.0)));
    }

    @Test
    @DisplayName("环比 Δ%: QPS 突降 ≥50% (对比基线)")
    void deltaPercentDrop() {
        // 突降: (baseline - value)/baseline*100 >= 50
        String dropExpr = "(baseline - value) / baseline * 100 >= 50";
        assertTrue(engine.evalBoolean(dropExpr, Map.of("value", 40.0, "baseline", 100.0)),  "降60%应命中");
        assertFalse(engine.evalBoolean(dropExpr, Map.of("value", 80.0, "baseline", 100.0)), "降20%不命中");
        // 突增: (value - baseline)/baseline*100 >= 50
        String riseExpr = "(value - baseline) / baseline * 100 >= 50";
        assertTrue(engine.evalBoolean(riseExpr, Map.of("value", 200.0, "baseline", 100.0)));
    }

    @Test
    @DisplayName("字段单位换算: 内存 (total-available)/total*100")
    void unitConversion() {
        Object r = engine.eval("(c2 - c7) / c2 * 100", Map.of("c2", 15884.0, "c7", 8890.0));
        assertNotNull(r);
        double expected = (15884 - 8890) * 100.0 / 15884;
        assertEquals(expected, ((Number) r).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("CPU 使用率换算: 100 - idle")
    void cpuUsageConversion() {
        Object r = engine.eval("100 - c15", Map.of("c15", 90.0));
        assertEquals(10.0, ((Number) r).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("表达式语法校验")
    void validateSyntax() {
        assertTrue(engine.validate("value >= 85"));
        assertTrue(engine.validate("(c2 - c7) / c2 * 100"));
        assertFalse(engine.validate("value >= "), "残缺表达式应校验失败");
    }

    @Test
    @DisplayName("缺失变量容错: 返回 null/false 而非抛异常 (silent 模式)")
    void missingVariableTolerant() {
        // value 未绑定, silent+strict=false 应容忍
        assertFalse(engine.evalBoolean("value >= 85", Map.of()), "缺变量不应命中");
    }

    @Test
    @DisplayName("编译缓存: 同一表达式多次求值结果一致")
    void cachedEvaluationConsistent() {
        String expr = "value >= 85";
        for (int i = 0; i < 100; i++) {
            assertTrue(engine.evalBoolean(expr, Map.of("value", 90.0)));
            assertFalse(engine.evalBoolean(expr, Map.of("value", 10.0)));
        }
    }
}
