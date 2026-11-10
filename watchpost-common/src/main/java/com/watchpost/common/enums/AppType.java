package com.watchpost.common.enums;

/** 监控对象类型 (PRD 四类: 服务器/API/三类数据库)。v1.0 不含 Windows。 */
public enum AppType {
    linux, http, mysql, postgresql, oracle;

    public static AppType of(String s) {
        for (AppType t : values()) if (t.name().equalsIgnoreCase(s)) return t;
        throw new IllegalArgumentException("unknown app_type: " + s);
    }
}
