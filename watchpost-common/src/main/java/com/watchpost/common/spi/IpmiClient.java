package com.watchpost.common.spi;

import java.util.Map;

/**
 * IPMI 底层客户端 SPI (带外降级采集)。
 * 默认 Verax IPMI 实现; 因 Verax 库年久, 提供 ipmitool 子进程封装兜底实现, 便于替换。
 */
public interface IpmiClient {

    /**
     * 读取传感器/状态。
     * @param host BMC 地址
     * @param credential username/password/cipherSuite/privilege
     * @param timeoutMs 超时 (BMC 响应慢, 建议 ≤5s)
     * @return 原始键值 (power_status/temp/fan/...), 由采集器做字段映射
     */
    Map<String, String> readSensors(String host, Map<String, String> credential, long timeoutMs);

    /** 探活 (mc info), 用于连通性预检。 */
    boolean probe(String host, Map<String, String> credential, long timeoutMs);
}
