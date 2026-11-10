package com.watchpost.manager.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计注解 (PRD 07: 登录与配置变更全量审计)。
 * 标注在 Controller/Service 方法上, 由 AuditAspect 拦截写入 wp_audit_log (详情脱敏)。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    /** 动作类型: LOGIN/LOGOUT/CONFIG_CHANGE/MONITOR_CREATE/ALERT_ACK/... */
    String action();
    /** 目标类型: MONITOR/RULE/USER/SMTP/... */
    String targetType() default "";
}
