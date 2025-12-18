package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(value = "SysRoleEntity对象", description = "角色表")
public class SysRoleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("角色ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("角色标识")
    @TableField("role_key")
    private String roleKey;

    @ApiModelProperty("角色名称")
    @TableField("role_name")
    private String roleName;

    @ApiModelProperty("父角色ID（用于继承）")
    @TableField("parent_id")
    private Long parentId;

    @ApiModelProperty("角色类型：1-系统角色 2-自定义角色")
    @TableField("role_type")
    private Byte roleType;

    @ApiModelProperty("排序")
    @TableField("sort_order")
    private Integer sortOrder;

    @ApiModelProperty("状态：1-启用 0-禁用")
    @TableField("status")
    private Byte status;

    @ApiModelProperty("创建人")
    @TableField("create_by")
    private Long createBy;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @ApiModelProperty("更新人")
    @TableField("update_by")
    private Long updateBy;

    @ApiModelProperty("更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;

    @ApiModelProperty("备注")
    @TableField("remark")
    private String remark;

    @ApiModelProperty("删除标志")
    @TableField("del_flag")
    @TableLogic
    private Byte delFlag;
}

/*
 * 非 Lombok 版本示例：
 * public class SysRoleEntity implements Serializable {
 *     private static final long serialVersionUID = 1L;
 *     private Long id; private String roleKey; private String roleName; private Long parentId;
 *     private Byte roleType; private Integer sortOrder; private Byte status;
 *     private Long createBy; private LocalDateTime createTime; private Long updateBy;
 *     private LocalDateTime updateTime; private String remark; private Byte delFlag;
 *
 *     public Long getId() { return id; }             public void setId(Long id) { this.id = id; }
 *     public String getRoleKey() { return roleKey; } public void setRoleKey(String roleKey) { this.roleKey = roleKey; }
 *     public String getRoleName() { return roleName; } public void setRoleName(String roleName) { this.roleName = roleName; }
 *     public Long getParentId() { return parentId; } public void setParentId(Long parentId) { this.parentId = parentId; }
 *     public Byte getRoleType() { return roleType; } public void setRoleType(Byte roleType) { this.roleType = roleType; }
 *     public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
 *     public Byte getStatus() { return status; } public void setStatus(Byte status) { this.status = status; }
 *     public Long getCreateBy() { return createBy; } public void setCreateBy(Long createBy) { this.createBy = createBy; }
 *     public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
 *     public Long getUpdateBy() { return updateBy; } public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
 *     public LocalDateTime getUpdateTime() { return updateTime; } public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
 *     public String getRemark() { return remark; } public void setRemark(String remark) { this.remark = remark; }
 *     public Byte getDelFlag() { return delFlag; } public void setDelFlag(Byte delFlag) { this.delFlag = delFlag; }
 * }
 */
