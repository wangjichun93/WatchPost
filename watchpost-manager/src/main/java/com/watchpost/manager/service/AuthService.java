package com.watchpost.manager.service;

import com.watchpost.common.dto.LoginRequest;
import com.watchpost.common.dto.LoginResponse;
import com.watchpost.common.enums.RoleCode;
import com.watchpost.common.exception.BizException;
import com.watchpost.common.exception.ErrorCode;
import com.watchpost.manager.audit.AuditService;
import com.watchpost.manager.security.CaptchaService;
import com.watchpost.manager.security.JwtService;
import com.watchpost.manager.security.LoginLockService;
import com.watchpost.warehouse.entity.RoleEntity;
import com.watchpost.warehouse.entity.UserEntity;
import com.watchpost.warehouse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务 (PRD 07/08: 登录保护 + 验证码 + 全量审计 + 记住我)。
 * 流程: 锁定检查 → 验证码检查 (失败≥2次要求) → 口令校验 (bcrypt) → 失败计数/锁定 → 成功签发 JWT + 审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginLockService lockService;
    private final CaptchaService captchaService;
    private final AuditService auditService;

    @Transactional
    public LoginResponse login(LoginRequest req, String ip, String userAgent) {
        String username = req.getUsername();
        Map<String, Object> auditDetail = new HashMap<>();
        auditDetail.put("ip", ip);

        // 1. 锁定检查
        if (lockService.isLocked(username)) {
            long remain = lockService.lockRemainingSec(username);
            auditService.record(null, username, "LOGIN", "USER", null, auditDetail, ip, userAgent, false);
            throw new BizException(ErrorCode.ACCOUNT_LOCKED, "剩余 " + (remain / 60 + 1) + " 分钟");
        }

        // 2. 验证码检查 (失败≥2次要求)
        if (lockService.captchaRequired(username)) {
            if (req.getCaptcha() == null || !captchaService.verify(req.getCaptchaKey(), req.getCaptcha())) {
                auditService.record(null, username, "LOGIN", "USER", null, auditDetail, ip, userAgent, false);
                throw new BizException(ErrorCode.CAPTCHA_ERROR);
            }
        }

        // 3. 用户与口令校验
        UserEntity user = userRepo.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            boolean locked = lockService.recordFailure(username);
            auditService.record(user == null ? null : user.getId(), username, "LOGIN", "USER",
                    user == null ? null : String.valueOf(user.getId()), auditDetail, ip, userAgent, false);
            if (locked) {
                throw new BizException(ErrorCode.ACCOUNT_LOCKED, "连续5次失败, 锁定15分钟");
            }
            int remain = lockService.remainingAttempts(username);
            throw new BizException(ErrorCode.LOGIN_FAILED, "还可尝试 " + remain + " 次");
        }

        // 4. 账号状态
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            auditService.record(user.getId(), username, "LOGIN", "USER", String.valueOf(user.getId()), auditDetail, ip, userAgent, false);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 5. 成功: 清除失败计数, 更新登录信息, 签发令牌
        lockService.recordSuccess(username);
        user.setFailedCount(0);
        user.setLockUntil(null);
        user.setLastLoginAt(OffsetDateTime.now());
        user.setLastLoginIp(ip);
        userRepo.save(user);

        List<String> roles = user.getRoles().stream().map(r -> r.getCode().name()).toList();
        String access = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refresh = req.isRememberMe()
                ? jwtService.generateRefreshToken(user.getId(), user.getUsername(), roles) : null;

        auditService.record(user.getId(), username, "LOGIN", "USER", String.valueOf(user.getId()), auditDetail, ip, userAgent, true);
        log.info("user login success: {} roles={}", username, roles);

        return LoginResponse.builder()
                .accessToken(access)
                .tokenType("Bearer")
                .expiresIn(jwtService.accessTtlSec())
                .refreshToken(refresh)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId()).username(user.getUsername())
                        .displayName(user.getDisplayName()).roles(roles).build())
                .build();
    }

    /** 刷新令牌 (记住我: 7天内免登录)。 */
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || !jwtService.isValid(refreshToken)
                || !"refresh".equals(jwtService.tokenType(refreshToken))) {
            throw new BizException(ErrorCode.OLD_TOKEN_INVALID);
        }
        String username = jwtService.username(refreshToken);
        UserEntity user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        List<String> roles = user.getRoles().stream().map(r -> r.getCode().name()).toList();
        String access = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        return LoginResponse.builder()
                .accessToken(access).tokenType("Bearer").expiresIn(jwtService.accessTtlSec())
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId()).username(user.getUsername())
                        .displayName(user.getDisplayName()).roles(roles).build())
                .build();
    }

    /** 登出 (无状态 JWT: 仅审计; 令牌失效可后续接入 Redis 黑名单)。 */
    public void logout(String username, String ip, String userAgent) {
        auditService.record(null, username, "LOGOUT", "USER", null, Map.of("ip", ip), ip, userAgent, true);
    }

    /** 当前用户信息。 */
    public LoginResponse.UserInfo currentUser(Long userId) {
        UserEntity user = userRepo.findById(userId).orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        List<String> roles = user.getRoles().stream().map(r -> r.getCode().name()).toList();
        return LoginResponse.UserInfo.builder()
                .id(user.getId()).username(user.getUsername())
                .displayName(user.getDisplayName()).roles(roles).build();
    }

    /** 角色码工具 (供初始化器使用)。 */
    public static List<RoleCode> allRoles() {
        return List.of(RoleCode.values());
    }
}
