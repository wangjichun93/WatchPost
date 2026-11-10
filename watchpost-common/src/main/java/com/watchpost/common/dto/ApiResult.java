package com.watchpost.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 统一 API 响应包络。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {
    private int code;          // 0 成功; 非0 业务错误码
    private String message;
    private T data;
    private long timestamp;

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data, System.currentTimeMillis());
    }
    public static <T> ApiResult<T> ok() {
        return ok(null);
    }
    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null, System.currentTimeMillis());
    }
}
