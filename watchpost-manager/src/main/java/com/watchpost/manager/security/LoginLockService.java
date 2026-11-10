package com.watchpost.manager.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录保护服务 (PRD 07: 连续5次失败锁定15分钟, 验证码, 登录全量审计)。
 * Redis 计数: login:fail:{username} 累计失败次数; login:lock:{username} 锁定标记。
 * 失败达阈值 (默认5) 锁定 15min; 锁定期间要求验证码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLockService {

    private final StringRedisTemplate redis;

    private static final String K_FAIL = "login:fail:";
    private static final String K_LOCK = "login:lock:";
    private static final int MAX_FAIL = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(15);

    /** 是否处于锁定状态。 */
    public boolean isLocked(String username) {
        return Boolean.TRUE.equals(redis.hasKey(K_LOCK + username));
    }

    /** 锁定剩余秒数 (0=未锁定)。 */
    public long lockRemainingSec(String username) {
        Long ttl = redis.getExpire(K_LOCK + username);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    /** 是否需要验证码 (失败≥2次即要求, 降低暴力破解)。 */
    public boolean captchaRequired(String username) {
        String fails = redis.opsForValue().get(K_FAIL + username);
        return fails != null && Integer.parseInt(fails) >= 2;
    }

    /** 剩余可尝试次数。 */
    public int remainingAttempts(String username) {
        String fails = redis.opsForValue().get(K_FAIL + username);
        int n = fails == null ? 0 : Integer.parseInt(fails);
        return Math.max(0, MAX_FAIL - n);
    }

    /**
     * 记录一次失败。返回是否触发锁定。
     */
    public boolean recordFailure(String username) {
        String key = K_FAIL + username;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, FAIL_WINDOW);
        }
        if (count != null && count >= MAX_FAIL) {
            redis.opsForValue().set(K_LOCK + username, "1", LOCK_DURATION);
            redis.delete(key);
            log.warn("account locked due to {} consecutive failures: username={}", MAX_FAIL, username);
            return true;
        }
        return false;
    }

    /** 登录成功: 清除失败计数与锁定。 */
    public void recordSuccess(String username) {
        redis.delete(K_FAIL + username);
        redis.delete(K_LOCK + username);
    }
}
