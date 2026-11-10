package com.watchpost.common.enums;

/** RBAC 三角色 (PRD 第06/08章)。 */
public enum RoleCode {
    ADMIN("管理员"), OPERATOR("运维"), READONLY("只读");
    private final String label;
    RoleCode(String label) { this.label = label; }
    public String label() { return label; }
    /** Spring Security 权限前缀: ROLE_ADMIN 等。 */
    public String authority() { return "ROLE_" + name(); }
}
