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
 * 权限表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_permission")
@ApiModel(value = "SysPermissionEntity对象", description = "权限表")
public class SysPermissionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("权限ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("权限标识（如：user:add）")
    @TableField("perm_key")
    private String permKey;

    @ApiModelProperty("权限名称")
    @TableField("perm_name")
    private String permName;

    @ApiModelProperty("资源类型：1-菜单 2-API 3-数据")
    @TableField("resource_type")
    private Byte resourceType;

    @ApiModelProperty("资源ID")
    @TableField("resource_id")
    private Long resourceId;

    @ApiModelProperty("父权限ID")
    @TableField("parent_id")
    private Long parentId;

    @ApiModelProperty("排序")
    @TableField("sort_order")
    private Integer sortOrder;

    @ApiModelProperty("状态：1-启用 0-禁用")
    @TableField("status")
    private Byte status;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}
