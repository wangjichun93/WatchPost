package com.watchpost.manager.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 验证码服务 (PRD 07: 连续失败后要求验证码)。
 * v1 实现: 生成 4 位数字验证码, 存 Redis (captcha:{key} -> code, TTL 5min), 前端图形化由 /api/auth/captcha 返回。
 * 说明: 生产可替换为 tianai-captcha 等图形/行为验证码; 此处为可编译的最小可用实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    private static final String PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);

    /** 生成验证码, 返回 (key, code)。key 下发前端, code 仅存 Redis。 */
    public Captcha generate() {
        String key = UUID.randomUUID().toString().replace("-", "");
        String code = String.format("%04d", random.nextInt(10000));
        redis.opsForValue().set(PREFIX + key, code, TTL);
        return new Captcha(key, code);
    }

    /** 校验验证码 (一次性, 校验后删除)。 */
    public boolean verify(String key, String code) {
        if (key == null || code == null) return false;
        String cached = redis.opsForValue().get(PREFIX + key);
        redis.delete(PREFIX + key);   // 一次性
        return code.equalsIgnoreCase(cached);
    }

    public record Captcha(String key, String code) {}
}
