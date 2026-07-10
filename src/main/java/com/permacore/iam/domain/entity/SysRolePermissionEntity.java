package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "SysRolePermissionEntity", description = "角色权限关联表")
public class SysRolePermissionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "关联ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "角色ID")
    @TableField("role_id")
    private Long roleId;

    @Schema(description = "权限ID")
    @TableField("permission_id")
    private Long permissionId;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}
