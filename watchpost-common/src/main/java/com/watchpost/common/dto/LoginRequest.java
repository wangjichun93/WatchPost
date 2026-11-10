package com.watchpost.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录请求 (PRD 第08章: 用户名/工号 + 密码 + 验证码 + 记住我)。 */
@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    /** 验证码 (连续失败后要求) */
    private String captcha;
    private String captchaKey;
    /** 记住我: 7 天内免登录 */
    private boolean rememberMe;
}
