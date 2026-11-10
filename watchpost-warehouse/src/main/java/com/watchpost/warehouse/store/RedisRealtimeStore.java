package com.watchpost.warehouse.store;

import com.watchpost.common.model.MetricValue;
import com.watchpost.common.spi.RealtimeStore;
import com.watchpost.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 实时值存储 (默认实现)。
 * 结构:
 *   rt:latest:{monitorId}      Hash  field=group#field -> Latest(JSON)   最新值快照
 *   rt:health:{monitorId}      Hash  status / connFailCount / ts          健康态
 *   rt:hit:{fingerprint}       String 连续命中计数 (带窗口 TTL)            收敛: 连续N次
 *   rt:sup:{fingerprint}       String 抑制标记 (带 suppressSec TTL)        收敛: 抑制窗口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRealtimeStore implements RealtimeStore {

    private final StringRedisTemplate redis;

    private static final String K_LATEST = "rt:latest:";
    private static final String K_HEALTH = "rt:health:";
    private static final String K_HIT = "rt:hit:";
    private static final String K_SUP = "rt:sup:";

    @Override
    public void putLatest(long monitorId, List<MetricValue> values, long timestamp, boolean success, int connFailCount) {
        if (values == null || values.isEmpty()) return;
        Map<String, String> hash = new HashMap<>();
        for (MetricValue v : values) {
            Latest l = new Latest(v.getMetricGroup(), v.getFieldName(), v.getDoubleValue(),
                    v.getStrValue(), v.getUnit(), timestamp, success, v.getLabels());
            hash.put(l.key(), JsonUtil.toJson(l));
        }
        redis.opsForHash().putAll(K_LATEST + monitorId, hash);
        // 实时值保留 7 天 (远大于采集周期, 兜底防泄漏)
        redis.expire(K_LATEST + monitorId, Duration.ofDays(7));
    }

    @Override
    public Map<String, Latest> getLatest(long monitorId) {
        Map<Object, Object> raw = redis.opsForHash().entries(K_LATEST + monitorId);
        Map<String, Latest> out = new HashMap<>();
        raw.forEach((k, v) -> out.put(String.valueOf(k), JsonUtil.fromJson(String.valueOf(v), Latest.class)));
        return out;
    }

    @Override
    public Latest getLatest(long monitorId, String group, String field) {
        Object v = redis.opsForHash().get(K_LATEST + monitorId, group + "#" + field);
        return v == null ? null : JsonUtil.fromJson(String.valueOf(v), Latest.class);
    }

    @Override
    public void updateHealth(long monitorId, int status, int connFailCount) {
        Map<String, String> h = new HashMap<>();
        h.put("status", String.valueOf(status));
        h.put("connFailCount", String.valueOf(connFailCount));
        h.put("ts", String.valueOf(System.currentTimeMillis()));
        redis.opsForHash().putAll(K_HEALTH + monitorId, h);
        redis.expire(K_HEALTH + monitorId, Duration.ofDays(7));
    }

    @Override
    public long incrHit(String fingerprint, int windowSec) {
        String key = K_HIT + fingerprint;
        Long n = redis.opsForValue().increment(key);
        // 首次命中设置窗口 TTL; 窗口内累计, 过期自动清零
        if (n != null && n == 1L) {
            redis.expire(key, Duration.ofSeconds(windowSec));
        }
        return n == null ? 0 : n;
    }

    @Override
    public void resetHit(String fingerprint) {
        redis.delete(K_HIT + fingerprint);
    }

    @Override
    public boolean acquireSuppress(String fingerprint, int suppressSec) {
        String key = K_SUP + fingerprint;
        // setIfAbsent: 抑制窗口内首次返回 true (允许通知), 之后返回 false (抑制)
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(suppressSec));
        return Boolean.TRUE.equals(ok);
    }
}
