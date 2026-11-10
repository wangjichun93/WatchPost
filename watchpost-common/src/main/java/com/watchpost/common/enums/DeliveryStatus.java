package com.watchpost.common.enums;

/** 邮件投递状态 (wp_delivery_log.status)。 */
public enum DeliveryStatus {
    PENDING(0, "待发"), SUCCESS(1, "成功"), FAILED(2, "失败"), BOUNCED(3, "退信");

    private final int code; private final String label;
    DeliveryStatus(int code, String label) { this.code = code; this.label = label; }
    public int code() { return code; }
    public String label() { return label; }
    public static DeliveryStatus of(int code) {
        for (DeliveryStatus s : values()) if (s.code == code) return s;
        return PENDING;
    }
}
