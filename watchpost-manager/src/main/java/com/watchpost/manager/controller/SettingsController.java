package com.watchpost.manager.controller;

import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.spi.CryptoService;
import com.watchpost.manager.audit.Auditable;
import com.watchpost.warehouse.entity.CollectorNodeEntity;
import com.watchpost.warehouse.entity.SmtpConfigEntity;
import com.watchpost.warehouse.entity.UserEntity;
import com.watchpost.warehouse.repository.CollectorNodeRepository;
import com.watchpost.warehouse.repository.SmtpConfigRepository;
import com.watchpost.warehouse.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统设置控制器 (PRD 06: /settings SMTP/凭据/采集器/用户与角色, 仅管理员)。
 * SMTP 授权码加密存储; 响应脱敏 (不回传密文/明文)。
 */
@Tag(name = "系统设置", description = "SMTP + 采集器 + 用户与角色")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SmtpConfigRepository smtpRepo;
    private final CollectorNodeRepository collectorRepo;
    private final UserRepository userRepo;
    private final CryptoService crypto;

    // ---- SMTP ----
    @Operation(summary = "获取 SMTP 配置 (授权码脱敏)")
    @GetMapping("/smtp")
    public ApiResult<Map<String, Object>> getSmtp() {
        return smtpRepo.findFirstByEnabledTrueOrderByIdDesc()
                .map(c -> ApiResult.ok(Map.<String, Object>of(
                        "id", c.getId(), "host", c.getHost(), "port", c.getPort(),
                        "starttls", c.getStarttls(), "sender", c.getSender(),
                        "authCipher", "***", "enabled", c.getEnabled())))
                .orElseGet(() -> ApiResult.ok(Map.of()));
    }

    @Operation(summary = "保存 SMTP 配置 (授权码 AES-256-GCM 加密)")
    @PostMapping("/smtp")
    @Auditable(action = "SMTP_SAVE", targetType = "SMTP")
    public ApiResult<Void> saveSmtp(@RequestBody Map<String, Object> body) {
        SmtpConfigEntity cfg = smtpRepo.findFirstByEnabledTrueOrderByIdDesc().orElseGet(SmtpConfigEntity::new);
        cfg.setHost(String.valueOf(body.get("host")));
        cfg.setPort(body.get("port") instanceof Number n ? n.intValue() : 465);
        cfg.setStarttls(Boolean.parseBoolean(String.valueOf(body.getOrDefault("starttls", true))));
        cfg.setSender(String.valueOf(body.get("sender")));
        Object auth = body.get("authCode");
        if (auth != null && !String.valueOf(auth).isBlank() && !"***".equals(String.valueOf(auth))) {
            cfg.setAuthCipher(crypto.encrypt(String.valueOf(auth)));
            cfg.setKmsKeyId(crypto.keyId());
        }
        cfg.setEnabled(true);
        smtpRepo.save(cfg);
        return ApiResult.ok();
    }

    // ---- 采集器 ----
    @Operation(summary = "采集器节点列表")
    @GetMapping("/collectors")
    public ApiResult<List<CollectorNodeEntity>> collectors() {
        return ApiResult.ok(collectorRepo.findAll());
    }

    // ---- 用户与角色 ----
    @Operation(summary = "用户列表 (口令哈希脱敏)")
    @GetMapping("/users")
    public ApiResult<List<Map<String, Object>>> users() {
        List<Map<String, Object>> out = userRepo.findAll().stream().map(u -> Map.<String, Object>of(
                "id", u.getId(), "username", u.getUsername(),
                "displayName", u.getDisplayName() == null ? "" : u.getDisplayName(),
                "email", u.getEmail() == null ? "" : u.getEmail(),
                "enabled", u.getEnabled(),
                "roles", u.getRoles().stream().map(r -> r.getCode().name()).toList())).toList();
        return ApiResult.ok(out);
    }

    @Operation(summary = "启用/停用用户")
    @PutMapping("/users/{id}/toggle")
    @Auditable(action = "USER_TOGGLE", targetType = "USER")
    public ApiResult<Void> toggleUser(@PathVariable long id, @RequestParam boolean enabled) {
        userRepo.findById(id).ifPresent(u -> { u.setEnabled(enabled); userRepo.save(u); });
        return ApiResult.ok();
    }

    @Operation(summary = "重置用户口令")
    @PutMapping("/users/{id}/reset-password")
    @Auditable(action = "USER_RESET_PWD", targetType = "USER")
    public ApiResult<Void> resetPassword(@PathVariable long id, @RequestBody Map<String, String> body) {
        UserEntity u = userRepo.findById(id).orElse(null);
        if (u != null && body.get("password") != null) {
            u.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .encode(body.get("password")));
            userRepo.save(u);
        }
        return ApiResult.ok();
    }
}
