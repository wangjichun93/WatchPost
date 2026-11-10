package com.watchpost.common.enums;

/** 采集协议 (v1.0 五种, 已移除 WinRM)。 */
public enum ProtocolType {
    ssh, snmp, ipmi, http, jdbc;

    public static ProtocolType of(String s) {
        for (ProtocolType t : values()) if (t.name().equalsIgnoreCase(s)) return t;
        throw new IllegalArgumentException("unknown protocol: " + s);
    }
}
