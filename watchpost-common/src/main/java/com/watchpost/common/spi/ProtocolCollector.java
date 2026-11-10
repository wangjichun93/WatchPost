package com.watchpost.common.spi;

import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MonitorTarget;

/**
 * 协议采集器 SPI。五协议各一实现 (SSH/SNMP/IPMI/HTTP/JDBC), v1.0 无 WinRM。
 * 实现要求: 主动 PULL、单向出站、硬超时 (target.timeoutMs)、只读、失败仅返回 success=false 不抛断调度。
 */
public interface ProtocolCollector {

    /** 支持的协议类型 (调度器按此路由)。 */
    ProtocolType protocol();

    /**
     * 执行一次采集。实现内部必须自施超时, 异常需捕获并落到 MetricRecord.error。
     * @param target 运行时只读快照 (含已解密凭据)
     * @return 标准化后的采集结果 (字段映射/单位换算已完成)
     */
    MetricRecord collect(MonitorTarget target);

    /**
     * 连通性预检 (新增监控时调用, 失败不落库 — PRD 要求)。
     * @return true 可达且认证通过
     */
    boolean precheck(MonitorTarget target);
}
