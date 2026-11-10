package com.watchpost.common.model;

import com.watchpost.common.enums.FieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单个指标字段的一次采集值 (采集器输出的最小单元)。
 * 由 ProtocolCollector 产出, 经标准化/单位换算后写入 RealtimeStore + TimeSeriesStore, 并发布到 MetricQueue。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricValue {
    /** 指标组: cpu/memory/disk/load/io/tcp/process/api/connections/... */
    private String metricGroup;
    /** 字段名: usage_percent/await_ms/... */
    private String fieldName;
    private FieldType fieldType;
    /** 数值型取值 (isNumeric 时有效) */
    private Double doubleValue;
    /** 字符串/布尔型取值 */
    private String strValue;
    private String unit;
    /** 标签维度 (如 disk 的 mountpoint, 数据库的 tablespace 名) */
    private Map<String, String> labels;

    public boolean isNumeric() {
        return fieldType != null && fieldType.isNumeric();
    }

    /** 判定用统一取值: 数值返回 double, 布尔/字符串返回 hash 或 0/1 语义由规则层处理。 */
    public Object rawValue() {
        return isNumeric() ? doubleValue : strValue;
    }
}
