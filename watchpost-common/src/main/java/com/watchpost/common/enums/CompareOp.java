package com.watchpost.common.enums;

/** 比较表达式 (PRD 第04章): > ≥ < ≤ == != / between / 环比 Δ%。 */
public enum CompareOp {
    GT, GE, LT, LE, EQ, NE, BETWEEN, DELTA_PCT;

    public String symbol() {
        return switch (this) {
            case GT -> ">"; case GE -> ">="; case LT -> "<"; case LE -> "<=";
            case EQ -> "=="; case NE -> "!="; case BETWEEN -> "between"; case DELTA_PCT -> "Δ%";
        };
    }
}
