package com.watchpost.common.model;

import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.ProtocolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 采集运行时视图: 调度器派发给 ProtocolCollector 的只读快照。
 * 由 manager/warehouse 从 wp_monitor + wp_credential + 模板组装, 凭据在此为「已解密」的瞬时值, 用后即弃。
 * 不持久化、不进日志 (toString 已脱敏)。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MonitorTarget {
    private long monitorId;
    private String monitorName;
    private AppType appType;
    private ProtocolType protocol;
    private String host;
    private Integer port;
    private int intervalSec;
    /** 采集模板 YAML 解析后的规范 (命令/SQL/字段映射/单位换算/断言) */
    private CollectTemplate template;
    /** 已解密凭据 (username/secret/community/cipherSuite 等), 敏感 */
    private Map<String, String> credential;
    /** 标签 (用于告警路由) */
    private Map<String, String> tags;
    /** 扩展配置 (HTTP 型: method/path/headers/body/expectStatus; 其他预留)。来自 wp_monitor.extra_json。 */
    private Map<String, Object> extra;
    /** 采集超时 (ms), 默认 5000 (PRD: ≤5s) */
    @Builder.Default
    private long timeoutMs = 5000L;

    @Override
    public String toString() {
        return "MonitorTarget{id=" + monitorId + ", name=" + monitorName + ", app=" + appType
                + ", proto=" + protocol + ", host=" + host + ":" + port + ", cred=***}";
    }
}
