package com.watchpost.common.exception;

import lombok.Getter;

/** 业务异常 (携带错误码, 由 GlobalExceptionHandler 统一转 ApiResult)。 */
@Getter
public class BizException extends RuntimeException {
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }
    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.message() + ": " + detail);
        this.errorCode = errorCode;
    }
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }
    public int code() { return errorCode.code(); }
}
