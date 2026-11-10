package com.watchpost.manager.audit;

import com.watchpost.manager.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * 审计切面: 拦截 @Auditable 方法, 记录操作人/动作/目标/IP/结果到 wp_audit_log。
 * 详情脱敏: 仅记录方法名与参数个数, 不记录敏感参数值 (口令/密钥由业务层单独脱敏审计)。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result = null;
        boolean success = true;
        String error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            error = t.getMessage();
            throw t;
        } finally {
            try {
                record(pjp, auditable, success, error);
            } catch (Exception e) {
                log.debug("audit aspect record failed: {}", e.getMessage());
            }
        }
    }

    private void record(ProceedingJoinPoint pjp, Auditable auditable, boolean success, String error) {
        Long userId = null;
        String username = "anonymous";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            userId = lu.getUserId();
            username = lu.getUsername();
        }
        HttpServletRequest req = currentRequest();
        String ip = req != null ? clientIp(req) : null;
        String ua = req != null ? req.getHeader("User-Agent") : null;

        Map<String, Object> detail = new HashMap<>();
        detail.put("method", pjp.getSignature().toShortString());
        detail.put("args", pjp.getArgs().length);
        if (error != null) detail.put("error", error);

        auditService.record(userId, username, auditable.action(), auditable.targetType(),
                null, detail, ip, ua, success);
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
