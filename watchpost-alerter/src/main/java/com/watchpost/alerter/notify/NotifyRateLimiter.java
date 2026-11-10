package com.watchpost.alerter.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 通知限流器 (PRD 05: 单收件人组 30min 内最多 20 封)。
 * Redis 固定窗口计数: key=notify:limit:{groupId}:{windowSlot}, TTL=30min。
 * 单机降级: Redis 不可用时放行 (不因限流组件故障阻断告警)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyRateLimiter {

    private final StringRedisTemplate redis;

    private static final int MAX_PER_WINDOW = 20;
    private static final long WINDOW_MINUTES = 30;

    /** 尝试获取投递许可。true=允许, false=超限丢弃。 */
    public boolean tryAcquire(Long groupId) {
        try {
            long slot = System.currentTimeMillis() / (WINDOW_MINUTES * 60 * 1000);
            String key = "notify:limit:" + groupId + ":" + slot;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(WINDOW_MINUTES));
            }
            return count == null || count <= MAX_PER_WINDOW;
        } catch (Exception e) {
            log.warn("rate limiter redis error, fail-open: {}", e.getMessage());
            return true;   // fail-open: 不因限流组件故障阻断告警
        }
    }
}
