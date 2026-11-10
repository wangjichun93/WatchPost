package com.watchpost.common.enums;

/** 规则作用域。优先级: INSTANCE > TYPE > GLOBAL (FIELD 为单字段级, 等价 INSTANCE 粒度)。 */
public enum RuleScope {
    GLOBAL(0), TYPE(10), INSTANCE(20), FIELD(30);

    private final int priority;
    RuleScope(int priority) { this.priority = priority; }
    /** 数值大者优先 (上层覆写下层)。 */
    public int priority() { return priority; }
}
