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
 * 用户角色关联表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_user_role")
@ApiModel(value = "SysUserRoleEntity对象", description = "用户角色关联表")
public class SysUserRoleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("关联ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("用户ID")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty("角色ID")
    @TableField("role_id")
    private Long roleId;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}

/*
 * 非 Lombok 版本示例：
 * public class SysUserRoleEntity implements Serializable {
 *     private static final long serialVersionUID = 1L;
 *     private Long id; private Long userId; private Long roleId; private LocalDateTime createTime;
 *
 *     public Long getId() { return id; }
 *     public void setId(Long id) { this.id = id; }
 *     public Long getUserId() { return userId; }
 *     public void setUserId(Long userId) { this.userId = userId; }
 *     public Long getRoleId() { return roleId; }
 *     public void setRoleId(Long roleId) { this.roleId = roleId; }
 *     public LocalDateTime getCreateTime() { return createTime; }
 *     public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
 * }
 */
