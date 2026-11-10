package com.watchpost.manager.controller;

import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.enums.AlertState;
import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.Severity;
import com.watchpost.warehouse.repository.AlertRepository;
import com.watchpost.warehouse.repository.MonitorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 总览大盘控制器 (PRD 06: /dashboard 分类统计/告警级别分布, 全部角色)。
 */
@Tag(name = "总览大盘", description = "分类统计 + 告警级别分布")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MonitorRepository monitorRepo;
    private final AlertRepository alertRepo;

    private static final List<AlertState> ACTIVE =
            List.of(AlertState.PENDING, AlertState.TRIGGERED, AlertState.NOTIFYING, AlertState.ACKNOWLEDGED);

    @Operation(summary = "大盘统计", description = "监控对象分类计数 + 活动告警级别分布")
    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary() {
        Map<String, Object> out = new HashMap<>();

        // 监控对象总数与分类统计
        long total = monitorRepo.countByDeletedFalse();
        Map<String, Long> byType = new HashMap<>();
        for (AppType t : AppType.values()) {
            byType.put(t.name(), monitorRepo.findByDeletedFalseAndAppType(t).stream()
                    .filter(m -> Boolean.TRUE.equals(m.getEnabled())).count());
        }
        long unreachable = monitorRepo.countByDeletedFalseAndStatus(2);
        out.put("monitorTotal", total);
        out.put("monitorByType", byType);
        out.put("monitorUnreachable", unreachable);

        // 活动告警级别分布
        Map<String, Long> bySeverity = new HashMap<>();
        long activeTotal = 0;
        for (Severity s : Severity.values()) {
            long c = alertRepo.countBySeverityAndStateIn(s, ACTIVE);
            bySeverity.put(s.name(), c);
            activeTotal += c;
        }
        out.put("activeAlertTotal", activeTotal);
        out.put("activeAlertBySeverity", bySeverity);

        return ApiResult.ok(out);
    }
}
