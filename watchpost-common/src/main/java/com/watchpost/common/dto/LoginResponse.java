package com.watchpost.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 登录响应: JWT + 用户信息 + 角色。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String tokenType;      // Bearer
    private long expiresIn;        // 秒
    private String refreshToken;   // 记住我时下发
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String displayName;
        private List<String> roles;   // ADMIN/OPERATOR/READONLY
    }
}
