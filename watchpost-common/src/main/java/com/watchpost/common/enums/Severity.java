package com.watchpost.common.enums;

/** 告警级别 (四级, PRD 第04章)。ordinal 越大越严重, 用于值班视图降序排列。 */
public enum Severity {
    INFO(0, "提示"),
    WARN(1, "警告"),
    CRITICAL(2, "严重"),
    FATAL(3, "致命");

    private final int rank;
    private final String label;

    Severity(int rank, String label) { this.rank = rank; this.label = label; }
    public int rank() { return rank; }
    public String label() { return label; }
}
