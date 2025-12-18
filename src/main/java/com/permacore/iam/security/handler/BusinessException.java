package com.permacore.iam.security.handler;

import com.permacore.iam.domain.vo.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    // 显式提供 getter，避免 Lombok 注解未生效时编译报错
    public Integer fetchCode() {
        return this.code;
    }
}

/*
 * 非 Lombok 版本示例：
 * public class BusinessException extends RuntimeException {
 *     private final Integer code;
 *
 *     public BusinessException(String message) {
 *         super(message);
 *         this.code = ResultCode.ERROR.getCode();
 *     }
 *     public BusinessException(ResultCode resultCode) {
 *         super(resultCode.getMsg());
 *         this.code = resultCode.getCode();
 *     }
 *     public BusinessException(ResultCode resultCode, String message) {
 *         super(message);
 *         this.code = resultCode.getCode();
 *     }
 *     public Integer getCode() { return this.code; }
 * }
 */
