package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(value = "SysLoginLogEntity对象", description = "登录日志表")
public class SysLoginLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("日志ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("登录用户名")
    @TableField("username")
    private String username;

    @ApiModelProperty("登录IP")
    @TableField("ip_address")
    private String ipAddress;

    @ApiModelProperty("登录地点")
    @TableField("location")
    private String location;

    @ApiModelProperty("浏览器")
    @TableField("browser")
    private String browser;

    @ApiModelProperty("操作系统")
    @TableField("os")
    private String os;

    @ApiModelProperty("登录时间")
    @TableField("login_time")
    private LocalDateTime loginTime;

    @ApiModelProperty("登录状态：1-成功 0-失败")
    @TableField("status")
    private Byte status;

    @ApiModelProperty("登录消息")
    @TableField("message")
    private String message;
}
