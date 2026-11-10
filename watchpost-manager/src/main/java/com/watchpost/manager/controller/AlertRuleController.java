package com.watchpost.manager.controller;

import com.watchpost.alerter.rule.RuleEngine;
import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.enums.RuleScope;
import com.watchpost.common.exception.BizException;
import com.watchpost.common.exception.ErrorCode;
import com.watchpost.manager.audit.Auditable;
import com.watchpost.warehouse.entity.ThresholdRuleEntity;
import com.watchpost.warehouse.repository.ThresholdRuleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 阈值规则控制器 (PRD 06: /alert-rules 逐指标配置/默认模板, 管理员·运维)。
 * 保存即热加载生效 (RuleEngine.reload), 无需重启。
 */
@Tag(name = "阈值规则", description = "逐指标阈值配置 + 默认模板")
@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final ThresholdRuleRepository ruleRepo;
    private final RuleEngine ruleEngine;

    @Operation(summary = "查询全部启用规则")
    @GetMapping
    public ApiResult<List<ThresholdRuleEntity>> list(@RequestParam(required = false) String scope,
                                                     @RequestParam(required = false) String scopeRef) {
        if (scope != null && !scope.isBlank()) {
            return ApiResult.ok(ruleRepo.findByScopeAndScopeRefAndEnabledTrue(
                    RuleScope.valueOf(scope), scopeRef));
        }
        return ApiResult.ok(ruleRepo.findByEnabledTrue());
    }

    @Operation(summary = "查询某监控对象的生效规则 (含全局默认 + 实例覆写)")
    @GetMapping("/effective")
    public ApiResult<List<ThresholdRuleEntity>> effective(@RequestParam long monitorId,
                                                          @RequestParam String appType,
                                                          @RequestParam(required = false) String group,
                                                          @RequestParam(required = false) String field) {
        return ApiResult.ok(ruleEngine.resolveRules(monitorId, appType,
                group == null ? "" : group, field == null ? "" : field));
    }

    @Operation(summary = "查询单条规则")
    @GetMapping("/{id}")
    public ApiResult<ThresholdRuleEntity> get(@PathVariable long id) {
        return ruleRepo.findById(id).map(ApiResult::ok)
                .orElseGet(() -> ApiResult.fail(ErrorCode.NOT_FOUND.code(), "规则不存在"));
    }

    @Operation(summary = "新增/编辑规则 (保存即热加载生效)")
    @PostMapping
    @Auditable(action = "RULE_SAVE", targetType = "RULE")
    public ApiResult<ThresholdRuleEntity> save(@RequestBody ThresholdRuleEntity rule) {
        // 表达式/阈值校验
        if (rule.getOperator() == null || rule.getThresholdJson() == null) {
            throw new BizException(ErrorCode.RULE_INVALID, "operator/threshold required");
        }
        ThresholdRuleEntity saved = ruleRepo.save(rule);
        ruleEngine.reload();   // 热加载
        return ApiResult.ok(saved);
    }

    @Operation(summary = "启用/停用规则")
    @PutMapping("/{id}/toggle")
    @Auditable(action = "RULE_TOGGLE", targetType = "RULE")
    public ApiResult<ThresholdRuleEntity> toggle(@PathVariable long id, @RequestParam boolean enabled) {
        ThresholdRuleEntity rule = ruleRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "rule " + id));
        rule.setEnabled(enabled);
        ruleRepo.save(rule);
        ruleEngine.reload();
        return ApiResult.ok(rule);
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    @Auditable(action = "RULE_DELETE", targetType = "RULE")
    public ApiResult<Void> delete(@PathVariable long id) {
        ruleRepo.deleteById(id);
        ruleEngine.reload();
        return ApiResult.ok();
    }
}
