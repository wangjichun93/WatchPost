package com.watchpost.manager.controller;

import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.dto.LoginRequest;
import com.watchpost.common.dto.LoginResponse;
import com.watchpost.manager.audit.Auditable;
import com.watchpost.manager.security.CaptchaService;
import com.watchpost.manager.security.LoginUser;
import com.watchpost.manager.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器 (PRD 08: /login 独立路由, 未登录全站拦截)。
 * 公开端点: login / captcha / refresh; 其余需 JWT。
 */
@Tag(name = "认证", description = "登录/登出/验证码/令牌刷新")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    @Operation(summary = "登录", description = "用户名/工号 + 口令 + 验证码; 连续5次失败锁定15分钟")
    @PostMapping("/login")
    @Auditable(action = "LOGIN", targetType = "USER")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        LoginResponse resp = authService.login(req, clientIp(http), http.getHeader("User-Agent"));
        return ApiResult.ok(resp);
    }

    @Operation(summary = "获取验证码", description = "失败≥2次后要求; 返回 key + 图形(此处返回码值供骨架联调)")
    @GetMapping("/captcha")
    public ApiResult<Map<String, String>> captcha() {
        CaptchaService.Captcha c = captchaService.generate();
        // 生产: 返回 base64 图片; 骨架: 返回 key + code 便于联调 (前端应只展示图片)
        return ApiResult.ok(Map.of("key", c.key(), "code", c.code()));
    }

    @Operation(summary = "刷新令牌", description = "记住我: 7天内免登录")
    @PostMapping("/refresh")
    public ApiResult<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        return ApiResult.ok(authService.refresh(body.get("refreshToken")));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    @Auditable(action = "LOGOUT", targetType = "USER")
    public ApiResult<Void> logout(@AuthenticationPrincipal LoginUser user, HttpServletRequest http) {
        authService.logout(user == null ? "anonymous" : user.getUsername(), clientIp(http), http.getHeader("User-Agent"));
        return ApiResult.ok();
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public ApiResult<LoginResponse.UserInfo> me(@AuthenticationPrincipal LoginUser user) {
        return ApiResult.ok(authService.currentUser(user.getUserId()));
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
