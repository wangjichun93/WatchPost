package com.watchpost.common.enums;

/** 监控对象状态。 */
public enum MonitorStatus {
    DISABLED(0, "停用"),
    NORMAL(1, "正常"),
    UNREACHABLE(2, "不可达");

    private final int code; private final String label;
    MonitorStatus(int code, String label) { this.code = code; this.label = label; }
    public int code() { return code; }
    public String label() { return label; }

    public static MonitorStatus of(int code) {
        for (MonitorStatus s : values()) if (s.code == code) return s;
        return NORMAL;
    }
}
