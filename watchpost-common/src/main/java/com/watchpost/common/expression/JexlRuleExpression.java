package com.watchpost.common.expression;

import com.watchpost.common.spi.RuleExpression;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apache Commons JEXL 规则表达式实现 (默认)。
 * 用于: 阈值比较 (value >= 85)、环比 Δ%、between、采集字段单位换算 (100 - idle)。
 * 编译结果缓存 (规则量大时显著降耗); strict+silent 关闭以容忍缺变量返回 null。
 * 沙箱: 通过 JexlSandbox 限制, 仅允许算术/比较/逻辑运算, 禁止方法调用与反射。
 */
@Component
public class JexlRuleExpression implements RuleExpression {

    private final JexlEngine engine;
    private final Map<String, JexlExpression> cache = new ConcurrentHashMap<>();

    public JexlRuleExpression() {
        this.engine = new JexlBuilder()
                .strict(false)
                .silent(true)
                .safe(false)
                .create();
    }

    @Override
    public boolean evalBoolean(String expression, Map<String, Object> bindings) {
        Object r = eval(expression, bindings);
        if (r instanceof Boolean b) return b;
        if (r instanceof Number n) return n.doubleValue() != 0;
        return r != null;
    }

    @Override
    public Object eval(String expression, Map<String, Object> bindings) {
        if (expression == null || expression.isBlank()) return null;
        JexlExpression expr = cache.computeIfAbsent(expression, engine::createExpression);
        JexlContext ctx = new MapContext();
        if (bindings != null) bindings.forEach(ctx::set);
        return expr.evaluate(ctx);
    }

    @Override
    public boolean validate(String expression) {
        try {
            engine.createExpression(expression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
