package com.watchpost.manager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 令牌服务 (无状态 API 鉴权)。
 * 密钥来自配置 watchpost.security.jwt.secret (生产建议 KMS/环境注入, ≥256bit)。
 * 访问令牌短期 (默认2h); 记住我下发刷新令牌 (7天, PRD: 7天内免登录)。
 */
@Slf4j
@Component
public class JwtService {

    private final SecretKey key;
    private final long accessTtlSec;
    private final long refreshTtlSec;
    private final String issuer;

    public JwtService(@Value("${watchpost.security.jwt.secret}") String secret,
                      @Value("${watchpost.security.jwt.access-ttl-sec:7200}") long accessTtlSec,
                      @Value("${watchpost.security.jwt.refresh-ttl-sec:604800}") long refreshTtlSec,
                      @Value("${watchpost.security.jwt.issuer:watchpost}") String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be >= 32 bytes (256 bit)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSec = accessTtlSec;
        this.refreshTtlSec = refreshTtlSec;
        this.issuer = issuer;
    }

    public String generateAccessToken(Long userId, String username, List<String> roles) {
        return build(userId, username, roles, accessTtlSec, "access");
    }

    public String generateRefreshToken(Long userId, String username, List<String> roles) {
        return build(userId, username, roles, refreshTtlSec, "refresh");
    }

    private String build(Long userId, String username, List<String> roles, long ttlSec, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .claims(Map.of("uid", userId, "roles", roles, "typ", type))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSec * 1000))
                .signWith(key)
                .compact();
    }

    /** 解析并校验令牌, 失败抛 JwtException。 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try { parse(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    public String username(String token) { return parse(token).getSubject(); }

    @SuppressWarnings("unchecked")
    public List<String> roles(String token) {
        Object r = parse(token).get("roles");
        return r instanceof List ? (List<String>) r : List.of();
    }

    public Long userId(String token) {
        Object u = parse(token).get("uid");
        return u instanceof Number n ? n.longValue() : null;
    }

    public String tokenType(String token) {
        Object t = parse(token).get("typ");
        return t == null ? "access" : String.valueOf(t);
    }

    public long accessTtlSec() { return accessTtlSec; }
}
