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
 * 角色权限关联表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_role_permission")
@ApiModel(value = "SysRolePermissionEntity对象", description = "角色权限关联表")
public class SysRolePermissionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("关联ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("角色ID")
    @TableField("role_id")
    private Long roleId;

    @ApiModelProperty("权限ID")
    @TableField("permission_id")
    private Long permissionId;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}

/*
 * 非 Lombok 版本示例：
 * public class SysRolePermissionEntity implements Serializable {
 *     private static final long serialVersionUID = 1L;
 *     private Long id; private Long roleId; private Long permissionId; private LocalDateTime createTime;
 *
 *     public Long getId() { return id; }
 *     public void setId(Long id) { this.id = id; }
 *     public Long getRoleId() { return roleId; }
 *     public void setRoleId(Long roleId) { this.roleId = roleId; }
 *     public Long getPermissionId() { return permissionId; }
 *     public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
 *     public LocalDateTime getCreateTime() { return createTime; }
 *     public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
 * }
 */
