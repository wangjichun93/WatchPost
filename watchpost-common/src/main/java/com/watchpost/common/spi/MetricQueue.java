package com.watchpost.common.spi;

import com.watchpost.common.model.MetricRecord;

import java.util.function.Consumer;

/**
 * 采集→判定管道队列 SPI。v1 默认 Redis Streams; 集群期可替换 Kafka (业务代码不改)。
 * 解耦采集与告警判定, 保证越限→邮件 ≤60s 的异步低延迟链路。
 */
public interface MetricQueue {

    /** 发布一条采集结果到判定管道。 */
    void publish(MetricRecord record);

    /** 注册消费者 (alerter 订阅)。实现需保证并发安全与至少一次投递。 */
    void subscribe(String consumerGroup, Consumer<MetricRecord> handler);

    /** 队列积压深度 (自监控用)。 */
    long pendingCount();
}
