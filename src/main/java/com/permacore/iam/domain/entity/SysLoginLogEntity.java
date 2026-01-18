package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 登录日志表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_login_log")
@Schema(name = "SysLoginLogEntity", description = "登录日志表")
public class SysLoginLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "登录用户名")
    @TableField("username")
    private String username;

    @Schema(description = "登录IP")
    @TableField("ip_address")
    @JsonProperty("ipAddr")
    private String ipAddress;

    @Schema(description = "登录地点")
    @TableField("location")
    @JsonProperty("loginLocation")
    private String location;

    @Schema(description = "浏览器")
    @TableField("browser")
    private String browser;

    @Schema(description = "操作系统")
    @TableField("os")
    private String os;

    @Schema(description = "登录时间")
    @TableField("login_time")
    private LocalDateTime loginTime;

    @Schema(description = "登录状态：1-成功 0-失败")
    @TableField("status")
    private Byte status;

    @Schema(description = "登录消息")
    @TableField("message")
    @JsonProperty("msg")
    private String message;
}
