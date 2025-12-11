package com.permacore.iam.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一响应结果
 */
@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String msg;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    public Result() {
        this.timestamp = LocalDateTime.now();
    }

    public static <T> Result<T> success() {
        return new Result<T>()
                .setCode(ResultCode.SUCCESS.getCode())
                .setMsg(ResultCode.SUCCESS.getMsg());
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(ResultCode.SUCCESS.getCode())
                .setMsg(ResultCode.SUCCESS.getMsg())
                .setData(data);
    }

    public static <T> Result<T> error(String message) {
        return new Result<T>()
                .setCode(ResultCode.ERROR.getCode())
                .setMsg(message);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<T>()
                .setCode(resultCode.getCode())
                .setMsg(resultCode.getMsg());
    }

    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<T>()
                .setCode(resultCode.getCode())
                .setMsg(message);
    }
}