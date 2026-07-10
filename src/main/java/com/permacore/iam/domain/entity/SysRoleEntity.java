package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 角色表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_role")
@Schema(name = "SysRoleEntity", description = "角色表")
public class SysRoleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "角色标识")
    @TableField("role_key")
    private String roleKey;

    @Schema(description = "角色名称")
    @TableField("role_name")
    private String roleName;

    @Schema(description = "角色类型：1-系统角色 2-自定义角色")
    @TableField("role_type")
    private Byte roleType;

    @Schema(description = "排序")
    @TableField("sort_order")
    private Integer sortOrder;

    @Schema(description = "状态：1-启用 0-禁用")
    @TableField("status")
    private Byte status;

    @Schema(description = "创建人")
    @TableField("create_by")
    private Long createBy;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    @TableField("update_by")
    private Long updateBy;

    @Schema(description = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;

    @Schema(description = "删除标志")
    @TableField("del_flag")
    @TableLogic
    private Byte delFlag;
}
