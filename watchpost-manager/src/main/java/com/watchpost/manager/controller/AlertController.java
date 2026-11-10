package com.watchpost.manager.controller;

import com.watchpost.alerter.converge.AlertConvergenceService;
import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.dto.PageResult;
import com.watchpost.common.enums.AlertState;
import com.watchpost.common.enums.Severity;
import com.watchpost.manager.audit.Auditable;
import com.watchpost.manager.security.LoginUser;
import com.watchpost.warehouse.entity.AlertEntity;
import com.watchpost.warehouse.repository.AlertRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 告警中心控制器 (PRD 06: /alerts 确认/静默/关闭, 全部角色)。
 * 值班视图: 未确认活动告警常驻, 按级别降序。
 */
@Tag(name = "告警中心", description = "告警查询 + 确认 + 静默 + 关闭")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepo;
    private final AlertConvergenceService convergence;

    private static final List<AlertState> ACTIVE =
            List.of(AlertState.PENDING, AlertState.TRIGGERED, AlertState.NOTIFYING, AlertState.ACKNOWLEDGED);

    @Operation(summary = "分页查询告警")
    @GetMapping
    public ApiResult<PageResult<AlertEntity>> list(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Long monitorId,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AlertState st = state == null || state.isBlank() ? null : AlertState.valueOf(state);
        Severity sev = severity == null || severity.isBlank() ? null : Severity.valueOf(severity);
        // Specification 动态过滤 (规避 stringtype=unspecified 下 :param IS NULL 的 untyped-null 报错)
        // 排序: 级别降序 (enum 自然序 INFO<WARN<CRITICAL<FATAL) + 最近触发降序
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "severity")
                .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "lastTriggerAt"));
        Page<AlertEntity> p = alertRepo.findAll(
                com.watchpost.warehouse.spec.Specs.alerts(st, monitorId, sev),
                PageRequest.of(Math.max(0, page - 1), size, sort));
        return ApiResult.ok(PageResult.of(p.getContent(), p.getTotalElements(), page, size));
    }

    @Operation(summary = "值班视图: 未确认活动告警 (按级别降序常驻)")
    @GetMapping("/active")
    public ApiResult<List<AlertEntity>> active() {
        return ApiResult.ok(alertRepo.findByStateInOrderBySeverityDescLastTriggerAtDesc(ACTIVE));
    }

    @Operation(summary = "告警详情")
    @GetMapping("/{alertNo}")
    public ApiResult<AlertEntity> get(@PathVariable String alertNo) {
        return alertRepo.findByAlertNo(alertNo)
                .map(ApiResult::ok)
                .orElseGet(() -> ApiResult.fail(5003, "告警不存在"));
    }

    @Operation(summary = "确认告警 (记录操作人+时间, 写审计)")
    @PostMapping("/{alertNo}/ack")
    @Auditable(action = "ALERT_ACK", targetType = "ALERT")
    public ApiResult<AlertEntity> ack(@PathVariable String alertNo, @AuthenticationPrincipal LoginUser user) {
        return ApiResult.ok(convergence.acknowledge(alertNo, user == null ? "system" : user.getUsername()));
    }

    @Operation(summary = "静默告警 (如静默1h)")
    @PostMapping("/{alertNo}/silence")
    @Auditable(action = "ALERT_SILENCE", targetType = "ALERT")
    public ApiResult<AlertEntity> silence(@PathVariable String alertNo,
                                          @RequestParam(defaultValue = "3600") int seconds,
                                          @AuthenticationPrincipal LoginUser user) {
        return ApiResult.ok(convergence.silence(alertNo, seconds, user == null ? "system" : user.getUsername()));
    }

    @Operation(summary = "关闭告警")
    @PostMapping("/{alertNo}/close")
    @Auditable(action = "ALERT_CLOSE", targetType = "ALERT")
    public ApiResult<Void> close(@PathVariable String alertNo) {
        alertRepo.findByAlertNo(alertNo).ifPresent(a -> {
            a.setState(AlertState.CLOSED);
            a.setClosedAt(java.time.OffsetDateTime.now());
            alertRepo.save(a);
        });
        return ApiResult.ok();
    }

    @Operation(summary = "告警级别分布统计")
    @GetMapping("/stats")
    public ApiResult<Map<String, Long>> stats() {
        Map<String, Long> out = new java.util.HashMap<>();
        for (Severity s : Severity.values()) {
            out.put(s.name(), alertRepo.countBySeverityAndStateIn(s, ACTIVE));
        }
        out.put("ACTIVE_TOTAL", alertRepo.countByStateIn(ACTIVE));
        return ApiResult.ok(out);
    }
}
