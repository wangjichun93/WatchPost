package com.watchpost.common.exception;

/** 业务错误码 (HTTP 状态映射见 GlobalExceptionHandler)。 */
public enum ErrorCode {
    // 通用 1xxx
    BAD_REQUEST(1001, "请求参数错误"),
    UNAUTHORIZED(1002, "未认证或令牌失效"),
    FORBIDDEN(1003, "无权限访问"),
    NOT_FOUND(1004, "资源不存在"),
    CONFLICT(1005, "资源冲突"),
    INTERNAL_ERROR(1006, "服务内部错误"),

    // 认证 2xxx
    LOGIN_FAILED(2001, "账号或密码错误"),
    ACCOUNT_LOCKED(2002, "账号已锁定, 请稍后再试"),
    ACCOUNT_DISABLED(2003, "账号已停用"),
    CAPTCHA_ERROR(2004, "验证码错误"),
    OLD_TOKEN_INVALID(2005, "令牌无效或已过期"),

    // 监控对象 3xxx
    MONITOR_PRECHECK_FAILED(3001, "连通性预检失败, 未保存"),
    MONITOR_NOT_FOUND(3002, "监控对象不存在"),
    TEMPLATE_INVALID(3003, "采集模板无效"),

    // 凭据/加密 4xxx
    CREDENTIAL_DECRYPT_FAILED(4001, "凭据解密失败"),
    CRYPTO_KEY_MISSING(4002, "加密主密钥未配置"),

    // 规则/告警 5xxx
    RULE_INVALID(5001, "阈值规则无效"),
    RULE_EXPRESSION_INVALID(5002, "规则表达式语法错误"),
    ALERT_NOT_FOUND(5003, "告警不存在"),

    // 通知 6xxx
    SMTP_NOT_CONFIGURED(6001, "SMTP 未配置"),
    NOTIFY_FAILED(6002, "通知投递失败");

    private final int code;
    private final String message;
    ErrorCode(int code, String message) { this.code = code; this.message = message; }
    public int code() { return code; }
    public String message() { return message; }
}
