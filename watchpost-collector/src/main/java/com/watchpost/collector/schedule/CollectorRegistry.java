package com.watchpost.collector.schedule;

import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.spi.ProtocolCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 采集器注册表: 按协议路由到对应 ProtocolCollector 实现。
 * Spring 注入全部采集器 Bean (SSH/HTTP/JDBC/SNMP/IPMI), 启动时建立协议→实现映射。
 */
@Slf4j
@Component
public class CollectorRegistry {

    private final Map<ProtocolType, ProtocolCollector> collectors = new EnumMap<>(ProtocolType.class);

    public CollectorRegistry(List<ProtocolCollector> beans) {
        for (ProtocolCollector c : beans) {
            collectors.put(c.protocol(), c);
            log.info("registered collector: protocol={} impl={}", c.protocol(), c.getClass().getSimpleName());
        }
    }

    public ProtocolCollector get(ProtocolType protocol) {
        ProtocolCollector c = collectors.get(protocol);
        if (c == null) throw new IllegalArgumentException("no collector for protocol: " + protocol);
        return c;
    }

    public boolean supports(ProtocolType protocol) {
        return collectors.containsKey(protocol);
    }

    public Map<ProtocolType, ProtocolCollector> all() {
        return Map.copyOf(collectors);
    }
}
