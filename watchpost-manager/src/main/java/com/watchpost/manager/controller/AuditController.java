package com.watchpost.manager.controller;

import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.dto.PageResult;
import com.watchpost.warehouse.entity.AuditLogEntity;
import com.watchpost.warehouse.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志控制器 (PRD 06: /audit 登录与配置变更审计日志, 仅管理员)。
 */
@Tag(name = "审计日志", description = "登录与配置变更全量审计")
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditRepo;

    @Operation(summary = "分页查询审计日志")
    @GetMapping
    public ApiResult<PageResult<AuditLogEntity>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), size);
        Page<AuditLogEntity> p;
        if (action != null && !action.isBlank()) {
            p = auditRepo.findByActionOrderByCreateTimeDesc(action, pr);
        } else if (userId != null) {
            p = auditRepo.findByUserIdOrderByCreateTimeDesc(userId, pr);
        } else {
            p = auditRepo.findByOrderByCreateTimeDesc(pr);
        }
        return ApiResult.ok(PageResult.of(p.getContent(), p.getTotalElements(), page, size));
    }
}
