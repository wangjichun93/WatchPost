package com.watchpost.collector.schedule;

import com.watchpost.common.enums.ProtocolType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 协议舱壁 (Bulkhead): 按协议类型限制并发, 防止某类慢目标占满载体线程。
 * PRD: 单任务失败不阻塞全局调度; 采集超时 ≤5s。
 * 默认每协议并发上限可配 (collector.bulkhead.*), SSH/JDBC 较低 (重连接), HTTP 较高。
 */
@Slf4j
@Component
public class ProtocolBulkhead {

    private final Map<ProtocolType, Semaphore> semaphores = new EnumMap<>(ProtocolType.class);

    public ProtocolBulkhead(CollectorProperties props) {
        Map<String, Integer> limits = props.getBulkhead();
        for (ProtocolType p : ProtocolType.values()) {
            int permit = limits.getOrDefault(p.name(), props.getDefaultBulkhead());
            semaphores.put(p, new Semaphore(permit));
            log.info("bulkhead init: protocol={} permits={}", p, permit);
        }
    }

    /**
     * 申请协议许可。
     * @return true 获得许可 (须在 finally 释放); false 超时未获得 (跳过本次采集, 不阻塞)
     */
    public boolean tryAcquire(ProtocolType protocol, long timeoutMs) {
        try {
            return semaphores.get(protocol).tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release(ProtocolType protocol) {
        semaphores.get(protocol).release();
    }

    public int available(ProtocolType protocol) {
        return semaphores.get(protocol).availablePermits();
    }
}
