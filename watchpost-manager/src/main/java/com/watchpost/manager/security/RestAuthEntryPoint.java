package com.watchpost.manager.security;

import com.watchpost.common.dto.ApiResult;
import com.watchpost.common.exception.ErrorCode;
import com.watchpost.common.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 未认证入口: 返回 401 + 统一 ApiResult (前端据此重定向 /login)。 */
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonUtil.toJson(
                ApiResult.fail(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.message())));
    }
}
