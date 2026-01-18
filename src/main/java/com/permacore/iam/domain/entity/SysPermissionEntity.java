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
 * 权限表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_permission")
@Schema(name = "SysPermissionEntity", description = "权限表")
public class SysPermissionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "权限ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "权限标识（如：user:add）")
    @TableField("perm_key")
    private String permKey;

    @Schema(description = "权限名称")
    @TableField("perm_name")
    private String permName;

    @Schema(description = "资源类型：1-菜单 2-API 3-数据")
    @TableField("resource_type")
    private Byte resourceType;

    @Schema(description = "资源ID")
    @TableField("resource_id")
    private Long resourceId;

    @Schema(description = "父权限ID")
    @TableField("parent_id")
    private Long parentId;

    @Schema(description = "排序")
    @TableField("sort_order")
    private Integer sortOrder;

    @Schema(description = "状态：1-启用 0-禁用")
    @TableField("status")
    private Byte status;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}

/*
 * 非 Lombok 版本示例：
 * public class SysPermissionEntity implements Serializable {
 * private static final long serialVersionUID = 1L;
 * private Long id;
 * private String permKey;
 * private String permName;
 * private Byte resourceType;
 * private Long resourceId;
 * private Long parentId;
 * private Integer sortOrder;
 * private Byte status;
 * private LocalDateTime createTime;
 *
 * public Long getId() { return id; }
 * public void setId(Long id) { this.id = id; }
 * public String getPermKey() { return permKey; }
 * public void setPermKey(String permKey) { this.permKey = permKey; }
 * public String getPermName() { return permName; }
 * public void setPermName(String permName) { this.permName = permName; }
 * public Byte getResourceType() { return resourceType; }
 * public void setResourceType(Byte resourceType) { this.resourceType =
 * resourceType; }
 * public Long getResourceId() { return resourceId; }
 * public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
 * public Long getParentId() { return parentId; }
 * public void setParentId(Long parentId) { this.parentId = parentId; }
 * public Integer getSortOrder() { return sortOrder; }
 * public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
 * public Byte getStatus() { return status; }
 * public void setStatus(Byte status) { this.status = status; }
 * public LocalDateTime getCreateTime() { return createTime; }
 * public void setCreateTime(LocalDateTime createTime) { this.createTime =
 * createTime; }
 * 
 * @Override
 * public String toString() {
 * return "SysPermissionEntity{" +
 * "id=" + id +
 * ", permKey='" + permKey + '\'' +
 * ", permName='" + permName + '\'' +
 * ", resourceType=" + resourceType +
 * ", resourceId=" + resourceId +
 * ", parentId=" + parentId +
 * ", sortOrder=" + sortOrder +
 * ", status=" + status +
 * ", createTime=" + createTime +
 * '}';
 * }
 * }
 */
