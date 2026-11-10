package com.watchpost.manager.controller;

import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.dto.MonitorRequest;
import com.watchpost.common.dto.MonitorView;
import com.watchpost.common.dto.PageResult;
import com.watchpost.common.enums.AppType;
import com.watchpost.common.spi.RealtimeStore;
import com.watchpost.common.spi.TimeSeriesStore;
import com.watchpost.manager.audit.Auditable;
import com.watchpost.manager.security.LoginUser;
import com.watchpost.manager.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 监控中心控制器 (PRD 06: /monitors 增删改查/标签/批量导入, 管理员·运维)。
 * /monitors/:id 详情 (实时指标卡/历史趋势图) 全部角色可读。
 */
@Tag(name = "监控中心", description = "监控对象增删改查 + 连通性预检 + 实时值")
@RestController
@RequestMapping("/api/monitors")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;
    private final RealtimeStore realtimeStore;
    private final TimeSeriesStore timeSeriesStore;

    @Operation(summary = "分页查询监控对象")
    @GetMapping
    public ApiResult<PageResult<MonitorView>> list(
            @RequestParam(required = false) String appType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AppType type = appType == null || appType.isBlank() ? null : AppType.of(appType);
        return ApiResult.ok(monitorService.search(type, status, keyword, page, size));
    }

    @Operation(summary = "监控详情")
    @GetMapping("/{id}")
    public ApiResult<MonitorView> get(@PathVariable long id) {
        return ApiResult.ok(monitorService.get(id));
    }

    @Operation(summary = "新增监控对象", description = "先连通性预检, 失败不落库 (PRD)")
    @PostMapping
    @Auditable(action = "MONITOR_CREATE", targetType = "MONITOR")
    public ApiResult<MonitorView> create(@Valid @RequestBody MonitorRequest req, @AuthenticationPrincipal LoginUser user) {
        return ApiResult.ok(monitorService.create(req, user == null ? null : user.getUserId()));
    }

    @Operation(summary = "编辑监控对象")
    @PutMapping("/{id}")
    @Auditable(action = "MONITOR_UPDATE", targetType = "MONITOR")
    public ApiResult<MonitorView> update(@PathVariable long id, @Valid @RequestBody MonitorRequest req) {
        return ApiResult.ok(monitorService.update(id, req));
    }

    @Operation(summary = "删除监控对象 (软删除)")
    @DeleteMapping("/{id}")
    @Auditable(action = "MONITOR_DELETE", targetType = "MONITOR")
    public ApiResult<Void> delete(@PathVariable long id) {
        monitorService.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "启用/停用")
    @PutMapping("/{id}/toggle")
    @Auditable(action = "MONITOR_TOGGLE", targetType = "MONITOR")
    public ApiResult<MonitorView> toggle(@PathVariable long id, @RequestParam boolean enabled) {
        return ApiResult.ok(monitorService.toggle(id, enabled));
    }

    @Operation(summary = "立即采集一次 (详情页手动刷新)")
    @PostMapping("/{id}/collect")
    public ApiResult<Void> collectNow(@PathVariable long id) {
        monitorService.collectNow(id);
        return ApiResult.ok();
    }

    @Operation(summary = "实时指标值 (详情页指标卡, 秒级读 Redis)")
    @GetMapping("/{id}/realtime")
    public ApiResult<Map<String, RealtimeStore.Latest>> realtime(@PathVariable long id) {
        return ApiResult.ok(realtimeStore.getLatest(id));
    }

    @Operation(summary = "历史趋势 (详情页趋势图, TSDB 降采样查询)")
    @GetMapping("/{id}/history")
    public ApiResult<List<TimeSeriesStore.Point>> history(
            @PathVariable long id,
            @RequestParam String group,
            @RequestParam String field,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "60") long step) {
        Map<String, List<TimeSeriesStore.Point>> data =
                timeSeriesStore.queryInterval(id, group, field, from, to, step);
        return ApiResult.ok(data.getOrDefault(group + "#" + field, List.of()));
    }
}
