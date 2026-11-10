package com.watchpost.collector.protocol;

import com.watchpost.common.enums.FieldType;
import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.model.MonitorTarget;
import com.watchpost.common.spi.IpmiClient;
import com.watchpost.common.spi.ProtocolCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IPMI 采集器 (带外降级路径)。通过 IpmiClient (默认 ipmitool 封装) 读取传感器/电源状态。
 * 非默认采集方式, 仅在无系统账号时由 SNMP+IPMI 带外兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpmiCollector implements ProtocolCollector {

    private final IpmiClient ipmiClient;

    @Override
    public ProtocolType protocol() {
        return ProtocolType.ipmi;
    }

    @Override
    public MetricRecord collect(MonitorTarget target) {
        long t0 = System.currentTimeMillis();
        MetricRecord.MetricRecordBuilder rb = MetricRecord.builder()
                .monitorId(target.getMonitorId()).monitorName(target.getMonitorName())
                .appType(target.getAppType()).protocol(ProtocolType.ipmi).timestamp(t0);
        List<MetricValue> values = new ArrayList<>();
        try {
            Map<String, String> sensors = ipmiClient.readSensors(
                    target.getHost(), target.getCredential(), target.getTimeoutMs());
            for (Map.Entry<String, String> e : sensors.entrySet()) {
                Double num = parseNumber(e.getValue());
                values.add(MetricValue.builder()
                        .metricGroup("ipmi").fieldName(sanitize(e.getKey()))
                        .fieldType(num != null ? FieldType.DOUBLE : FieldType.STRING)
                        .doubleValue(num).strValue(num != null ? null : e.getValue())
                        .build());
            }
            return rb.success(true).costMs(System.currentTimeMillis() - t0).values(values).build();
        } catch (Exception e) {
            log.debug("IPMI collect failed monitorId={}: {}", target.getMonitorId(), e.getMessage());
            return rb.success(false).error(e.getMessage())
                    .costMs(System.currentTimeMillis() - t0).values(values).build();
        }
    }

    @Override
    public boolean precheck(MonitorTarget target) {
        try {
            return ipmiClient.probe(target.getHost(), target.getCredential(), target.getTimeoutMs());
        } catch (Exception e) {
            return false;
        }
    }

    private Double parseNumber(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s.replaceAll("[^0-9.\\-]", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitize(String key) {
        return key.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}
