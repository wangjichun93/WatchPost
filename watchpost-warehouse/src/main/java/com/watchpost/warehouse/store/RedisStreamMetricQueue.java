package com.watchpost.warehouse.store;

import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.spi.MetricQueue;
import com.watchpost.common.util.JsonUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 指标队列 Redis Streams 实现 (集群可扩展, 可插拔)。
 * 激活: warehouse.queue.type=redis。
 *
 * 相比内存队列: 支持多采集器/多判定消费者跨进程、持久化、消费组 + ACK 至少一次投递。
 * 采集器 publish → XADD (近似裁剪 MAXLEN); alerter subscribe → 消费组 XREADGROUP 轮询 + 手动 XACK。
 * 集群期可平滑替换为 Kafka (MetricQueue SPI 不变)。
 *
 * 实现说明: 采用专用守护线程 XREADGROUP 阻塞轮询 (block 2s), 而非 StreamMessageListenerContainer,
 * 以规避其泛型装配复杂度并保持类型明确。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "warehouse.queue.type", havingValue = "redis")
public class RedisStreamMetricQueue implements MetricQueue {

    private final StringRedisTemplate redis;

    private static final String STREAM_KEY = "wp:metrics:stream";
    private static final String FIELD = "payload";
    private static final long MAX_LEN = 1_000_000L;   // 流长度上限 (近似裁剪, 防无限增长)

    private final List<Thread> workers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    /** 显式类型见证: StringRedisTemplate 的流视图 (hash key/value 为 Object)。 */
    private StreamOperations<String, Object, Object> ops() {
        return redis.opsForStream();
    }

    @Override
    public void publish(MetricRecord record) {
        if (record == null) return;
        try {
            String json = JsonUtil.toJson(record);
            ops().add(MapRecord.create(STREAM_KEY, Map.<String, String>of(FIELD, json)));
            // 近似裁剪 (MAXLEN ~): 高效, 允许略微超出上限
            ops().trim(STREAM_KEY, MAX_LEN, true);
        } catch (Exception e) {
            log.error("redis stream publish failed monitorId={}: {}", record.getMonitorId(), e.getMessage());
        }
    }

    @Override
    public void subscribe(String consumerGroup, java.util.function.Consumer<MetricRecord> handler) {
        ensureGroup(consumerGroup);
        String consumerName = consumerGroup + "-" + ProcessHandle.current().pid();
        Thread worker = new Thread(() -> pollLoop(consumerGroup, consumerName, handler),
                "redis-stream-" + consumerGroup);
        worker.setDaemon(true);
        workers.add(worker);
        worker.start();
        log.info("redis stream subscribed: group={} consumer={} stream={}", consumerGroup, consumerName, STREAM_KEY);
    }

    private void pollLoop(String group, String consumerName, java.util.function.Consumer<MetricRecord> handler) {
        StreamReadOptions readOpts = StreamReadOptions.empty().count(50).block(Duration.ofSeconds(2));
        StreamOffset<String> offset = StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed());
        org.springframework.data.redis.connection.stream.Consumer consumer =
                org.springframework.data.redis.connection.stream.Consumer.from(group, consumerName);
        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> records = ops().read(consumer, readOpts, offset);
                if (records == null || records.isEmpty()) continue;
                for (MapRecord<String, Object, Object> rec : records) {
                    RecordId id = rec.getId();
                    Object payload = rec.getValue().get(FIELD);
                    if (payload != null && !String.valueOf(payload).isEmpty()) {
                        try {
                            handler.accept(JsonUtil.fromJson(String.valueOf(payload), MetricRecord.class));
                        } catch (Exception e) {
                            log.error("redis stream handle error: {}", e.getMessage());
                        }
                    }
                    // 至少一次: 处理完(或异常)后 ACK, 避免 pending 堆积
                    try { ops().acknowledge(STREAM_KEY, group, id); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.debug("redis stream poll error: {}", e.getMessage());
                    sleep(1000);   // 退避, 避免 Redis 抖动时忙轮询
                }
            }
        }
    }

    @Override
    public long pendingCount() {
        try {
            Long size = ops().size(STREAM_KEY);
            return size == null ? 0 : size;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 确保消费组存在 (流不存在时先写占位记录创建流, 再建组)。 */
    private void ensureGroup(String group) {
        try {
            ops().createGroup(STREAM_KEY, ReadOffset.from("0"), group);
            log.info("redis stream group created: {}", group);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("BUSYGROUP")) return;   // 已存在, 正常
            try {
                // 流不存在: 写一条空占位记录创建流, 再建组
                ops().add(MapRecord.create(STREAM_KEY, Map.<String, String>of(FIELD, "")));
                ops().createGroup(STREAM_KEY, ReadOffset.from("0"), group);
            } catch (Exception e2) {
                if (e2.getMessage() != null && e2.getMessage().contains("BUSYGROUP")) return;
                log.warn("redis stream ensureGroup failed group={}: {}", group, e2.getMessage());
            }
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        workers.forEach(Thread::interrupt);
        workers.clear();
        log.info("redis stream queue shutdown");
    }
}
