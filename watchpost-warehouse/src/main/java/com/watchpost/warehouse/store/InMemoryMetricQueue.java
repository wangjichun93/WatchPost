package com.watchpost.warehouse.store;

import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.spi.MetricQueue;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 内存指标队列 (v1 单进程默认实现)。
 * collector 与 alerter 同进程, 内存队列延迟最低、无序列化开销;
 * 集群期通过 MetricQueue SPI 替换为 Redis Streams / Kafka (业务代码不改)。
 * 激活条件: warehouse.queue.type=memory (默认)。
 *
 * 有界队列 + 后台派发线程: 队列满时丢弃最旧记录并告警日志 (背压保护, 不阻塞采集)。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "warehouse.queue.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryMetricQueue implements MetricQueue {

    private static final int CAPACITY = 100_000;
    private final BlockingQueue<MetricRecord> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final List<Consumer<MetricRecord>> handlers = new CopyOnWriteArrayList<>();
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "metric-queue-dispatcher");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(true);

    public InMemoryMetricQueue() {
        dispatcher.submit(this::dispatchLoop);
    }

    private void dispatchLoop() {
        while (running.get()) {
            try {
                MetricRecord record = queue.poll(500, TimeUnit.MILLISECONDS);
                if (record == null) continue;
                for (Consumer<MetricRecord> h : handlers) {
                    try {
                        h.accept(record);
                    } catch (Exception e) {
                        // 单个消费者异常不影响其他消费者与派发循环
                        log.error("metric queue handler error, monitorId={}", record.getMonitorId(), e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void publish(MetricRecord record) {
        if (record == null) return;
        // 背压: 队列满时丢弃最旧记录, 保证采集不被阻塞 (PRD: 单任务失败不阻塞全局)
        while (!queue.offer(record)) {
            MetricRecord dropped = queue.poll();
            if (dropped != null) {
                log.warn("metric queue full (cap={}), dropped oldest record monitorId={}", CAPACITY, dropped.getMonitorId());
            }
        }
    }

    @Override
    public void subscribe(String consumerGroup, Consumer<MetricRecord> handler) {
        handlers.add(handler);
        log.info("metric queue subscribed: group={}", consumerGroup);
    }

    @Override
    public long pendingCount() {
        return queue.size();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        dispatcher.shutdownNow();
    }
}
