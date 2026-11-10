package com.watchpost.collector.protocol;

import com.watchpost.common.enums.FieldType;
import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.model.CollectTemplate;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.model.MonitorTarget;
import com.watchpost.common.spi.ProtocolCollector;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SNMP 采集器 (带外降级路径, PRD: 无系统账号时降级为 SNMP + IPMI)。
 * ⚠️ 许可注意: SNMP4J 为 AGPLv3/商业双许可, 商用前需法务评估; 兜底可替换为 net-snmp 命令行封装。
 * 对模板中声明的 OID 做 GET, 字段映射由模板 columnMapping/fields 定义。
 */
@Slf4j
@Component
public class SnmpCollector implements ProtocolCollector {

    private Snmp snmp;

    @jakarta.annotation.PostConstruct
    public void start() {
        try {
            DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();
            log.info("SNMP collector started (SNMP4J)");
        } catch (Exception e) {
            log.error("SNMP collector start failed", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (snmp != null) {
            try { snmp.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.snmp;
    }

    @Override
    public MetricRecord collect(MonitorTarget target) {
        long t0 = System.currentTimeMillis();
        MetricRecord.MetricRecordBuilder rb = MetricRecord.builder()
                .monitorId(target.getMonitorId()).monitorName(target.getMonitorName())
                .appType(target.getAppType()).protocol(ProtocolType.snmp).timestamp(t0);
        List<MetricValue> values = new ArrayList<>();
        try {
            if (snmp == null) throw new IllegalStateException("SNMP not initialized");
            Target<?> tgt = buildTarget(target);
            CollectTemplate tpl = target.getTemplate();
            // 模板 metrics[].fields[].name 约定为 OID 字符串 (或 extra 中 oids 映射)
            if (tpl != null && tpl.getMetrics() != null) {
                for (CollectTemplate.MetricGroup g : tpl.getMetrics()) {
                    if (g.getFields() == null) continue;
                    for (CollectTemplate.FieldDef f : g.getFields()) {
                        String oid = f.getExpr();   // 约定: expr 字段存放 OID
                        if (oid == null || !oid.matches("[0-9.]+")) continue;
                        Object val = get(tgt, oid, target.getTimeoutMs());
                        if (val != null) {
                            values.add(MetricValue.builder()
                                    .metricGroup(g.getGroup()).fieldName(f.getName())
                                    .fieldType(val instanceof Number ? FieldType.DOUBLE : FieldType.STRING)
                                    .doubleValue(val instanceof Number n ? n.doubleValue() : null)
                                    .strValue(val instanceof Number ? null : String.valueOf(val))
                                    .unit(f.getUnit()).build());
                        }
                    }
                }
            }
            return rb.success(true).costMs(System.currentTimeMillis() - t0).values(values).build();
        } catch (Exception e) {
            log.debug("SNMP collect failed monitorId={}: {}", target.getMonitorId(), e.getMessage());
            return rb.success(false).error(e.getMessage())
                    .costMs(System.currentTimeMillis() - t0).values(values).build();
        }
    }

    @Override
    public boolean precheck(MonitorTarget target) {
        try {
            if (snmp == null) return false;
            Target<?> tgt = buildTarget(target);
            // sysDescr.0 探活
            Object v = get(tgt, "1.3.6.1.2.1.1.1.0", target.getTimeoutMs());
            return v != null;
        } catch (Exception e) {
            log.info("SNMP precheck failed {} -> {}", target.getHost(), e.getMessage());
            return false;
        }
    }

    private Target<?> buildTarget(MonitorTarget target) {
        Map<String, String> cred = target.getCredential() == null ? Map.of() : target.getCredential();
        String community = cred.getOrDefault("community", cred.getOrDefault("password", "public"));
        int port = target.getPort() != null ? target.getPort() : 161;
        Address address = GenericAddress.parse("udp:" + target.getHost() + "/" + port);
        CommunityTarget target0 = new CommunityTarget();
        target0.setCommunity(new OctetString(community));
        target0.setAddress(address);
        target0.setRetries(1);
        target0.setTimeout(target.getTimeoutMs());
        target0.setVersion(SnmpConstants.version2c);
        return target0;
    }

    private Object get(Target<?> target, String oid, long timeoutMs) {
        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);
            ResponseEvent<?> event = snmp.send(pdu, target);
            if (event == null || event.getResponse() == null) return null;
            VariableBinding vb = event.getResponse().get(0);
            if (vb == null || vb.isException()) return null;
            Variable v = vb.getVariable();
            if (v instanceof Integer32 i) return i.getValue();
            if (v instanceof Gauge32 g) return g.getValue();
            if (v instanceof Counter32 c) return c.getValue();
            if (v instanceof Counter64 c) return c.getValue();
            return v.toString();
        } catch (Exception e) {
            log.debug("SNMP GET {} failed: {}", oid, e.getMessage());
            return null;
        }
    }
}
