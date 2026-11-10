package com.watchpost.manager.audit;

import com.watchpost.common.util.JsonUtil;
import com.watchpost.warehouse.entity.AuditLogEntity;
import com.watchpost.warehouse.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审计服务 (PRD 07: 登录与配置变更全量审计, 详情脱敏)。
 * 写入 wp_audit_log; 敏感字段 (口令/密钥/token) 由调用方或切面脱敏后传入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditRepo;

    /** 记录一条审计日志。 */
    public void record(Long userId, String username, String action, String targetType,
                       String targetId, Map<String, Object> detail, String ip, String userAgent, boolean success) {
        try {
            AuditLogEntity e = new AuditLogEntity();
            e.setUserId(userId);
            e.setUsername(username);
            e.setAction(action);
            e.setTargetType(targetType);
            e.setTargetId(targetId);
            e.setDetailJson(detail == null ? null : JsonUtil.toJson(detail));
            e.setIp(ip);
            e.setUserAgent(userAgent);
            e.setSuccess(success);
            auditRepo.save(e);
        } catch (Exception ex) {
            // 审计失败不阻断主流程, 仅告警日志
            log.error("audit record failed action={}: {}", action, ex.getMessage());
        }
    }

    /** 简化: 系统级事件 (无登录用户)。 */
    public void recordSystem(String action, String targetType, String targetId, Map<String, Object> detail, boolean success) {
        record(null, "system", action, targetType, targetId, detail, "127.0.0.1", "system", success);
    }
}
