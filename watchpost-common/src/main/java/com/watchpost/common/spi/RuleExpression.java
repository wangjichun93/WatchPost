package com.watchpost.common.spi;

import java.util.Map;

/**
 * 规则表达式引擎 SPI。默认 Apache Commons JEXL; 可替换 Aviator。
 * 用于阈值比较、环比 Δ%、between、字段换算 expr 求值。
 * 实现需缓存编译结果 (规则量大时显著降耗), 并对用户输入做沙箱/白名单约束。
 */
public interface RuleExpression {

    /**
     * 求值布尔表达式 (阈值判定)。
     * @param expression 如 "value >= 85" / "value between [14, 99]"
     * @param bindings   变量绑定 (value, baseline, labels...)
     */
    boolean evalBoolean(String expression, Map<String, Object> bindings);

    /**
     * 求值数值/字符串表达式 (单位换算, 如 "100 - idle")。
     * @return 计算结果 (Double 或 String)
     */
    Object eval(String expression, Map<String, Object> bindings);

    /** 校验表达式语法 (保存规则时预检)。 */
    boolean validate(String expression);
}
