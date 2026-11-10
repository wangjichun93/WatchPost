package com.watchpost.common.util;

/**
 * 脱敏工具 (PRD: 页面与日志一律脱敏)。
 * 口令/密钥/token/community 等敏感值在日志与 API 响应中统一掩码。
 */
public final class MaskUtil {

    private MaskUtil() {}

    /** 完全掩码 (口令/密钥): 固定返回 ***。 */
    public static String secret(String s) {
        return (s == null || s.isEmpty()) ? s : "***";
    }

    /** 部分掩码 (用户名/邮箱/主机): 保留首尾, 中间打码。 */
    public static String partial(String s) {
        if (s == null || s.length() <= 2) return s;
        int len = s.length();
        if (len <= 6) return s.charAt(0) + "***" + s.charAt(len - 1);
        return s.substring(0, 3) + "****" + s.substring(len - 3);
    }

    /** 邮箱掩码: ab***@domain.com。 */
    public static String email(String s) {
        if (s == null || !s.contains("@")) return partial(s);
        int at = s.indexOf('@');
        String name = s.substring(0, at);
        String domain = s.substring(at);
        if (name.length() <= 2) return name.charAt(0) + "***" + domain;
        return name.substring(0, 2) + "***" + domain;
    }

    /** IP 掩码: 10.20.3.*** 。 */
    public static String ip(String s) {
        if (s == null) return null;
        int idx = s.lastIndexOf('.');
        return idx > 0 ? s.substring(0, idx) + ".*" : s;
    }
}
