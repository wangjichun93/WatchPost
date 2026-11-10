package com.watchpost.common.enums;

/** 指标字段类型 (决定 TSDB 落盘列与解析方式)。 */
public enum FieldType {
    DOUBLE(0), LONG(0), INT(0), STRING(1), BOOL(1), TIME(2);

    private final int tsdbType;
    FieldType(int tsdbType) { this.tsdbType = tsdbType; }
    /** 0数值 1字符串 2时间 (对应 wp_metric_history.field_type)。 */
    public int tsdbType() { return tsdbType; }

    public boolean isNumeric() { return this == DOUBLE || this == LONG || this == INT; }
}
