package com.permacore.iam.domain.vo;

/**
 * 响应码枚举（显式 Getter，避免 Lombok 依赖）
 */
public enum ResultCode {
    // 成功
    SUCCESS(200, "操作成功"),

    // 客户端错误
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    // 服务端错误
    ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    // 业务错误
    USERNAME_EXISTS(1001, "用户名已存在"),
    ROLE_NOT_FOUND(1002, "角色不存在"),
    PERMISSION_DENIED(1003, "权限不足"),
    PASSWORD_ERROR(1004, "密码错误"),
    USER_LOCKED(1005, "用户已被锁定");

    private final Integer code;
    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}