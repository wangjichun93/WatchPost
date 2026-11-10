package com.watchpost.manager.controller;

import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.spi.Notifier;
import com.watchpost.manager.audit.Auditable;
import com.watchpost.warehouse.entity.NotifyGroupEntity;
import com.watchpost.warehouse.entity.NotifyPolicyEntity;
import com.watchpost.warehouse.repository.NotifyGroupRepository;
import com.watchpost.warehouse.repository.NotifyPolicyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 收件人组与通知策略控制器 (PRD 06: /notifications, 仅管理员)。
 * 含发送测试邮件 (PRD 05 可观测性要求)。
 */
@Tag(name = "通知管理", description = "收件人组 + 通知策略 + 测试邮件")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotifyGroupRepository groupRepo;
    private final NotifyPolicyRepository policyRepo;
    private final List<Notifier> notifiers;

    // ---- 收件人组 ----
    @Operation(summary = "收件人组列表")
    @GetMapping("/groups")
    public ApiResult<List<NotifyGroupEntity>> groups() {
        return ApiResult.ok(groupRepo.findAll());
    }

    @Operation(summary = "新增/编辑收件人组")
    @PostMapping("/groups")
    @Auditable(action = "NOTIFY_GROUP_SAVE", targetType = "NOTIFY_GROUP")
    public ApiResult<NotifyGroupEntity> saveGroup(@RequestBody NotifyGroupEntity group) {
        return ApiResult.ok(groupRepo.save(group));
    }

    @Operation(summary = "删除收件人组")
    @DeleteMapping("/groups/{id}")
    @Auditable(action = "NOTIFY_GROUP_DELETE", targetType = "NOTIFY_GROUP")
    public ApiResult<Void> deleteGroup(@PathVariable long id) {
        groupRepo.deleteById(id);
        return ApiResult.ok();
    }

    // ---- 通知策略 ----
    @Operation(summary = "通知策略列表")
    @GetMapping("/policies")
    public ApiResult<List<NotifyPolicyEntity>> policies() {
        return ApiResult.ok(policyRepo.findAll());
    }

    @Operation(summary = "新增/编辑通知策略")
    @PostMapping("/policies")
    @Auditable(action = "NOTIFY_POLICY_SAVE", targetType = "NOTIFY_POLICY")
    public ApiResult<NotifyPolicyEntity> savePolicy(@RequestBody NotifyPolicyEntity policy) {
        return ApiResult.ok(policyRepo.save(policy));
    }

    @Operation(summary = "删除通知策略")
    @DeleteMapping("/policies/{id}")
    @Auditable(action = "NOTIFY_POLICY_DELETE", targetType = "NOTIFY_POLICY")
    public ApiResult<Void> deletePolicy(@PathVariable long id) {
        policyRepo.deleteById(id);
        return ApiResult.ok();
    }

    // ---- 测试邮件 ----
    @Operation(summary = "发送测试邮件 (可观测性)")
    @PostMapping("/test")
    @Auditable(action = "NOTIFY_TEST", targetType = "NOTIFY")
    public ApiResult<Notifier.NotifyResult> test(@RequestBody Map<String, String> body) {
        String recipient = body.get("email");
        Notifier email = notifiers.stream().filter(n -> "EMAIL".equals(n.channel())).findFirst().orElse(null);
        if (email == null) return ApiResult.fail(6001, "无邮件通知渠道");
        return ApiResult.ok(email.sendTest(recipient));
    }
}
