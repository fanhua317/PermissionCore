package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 角色继承关系表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_role_inheritance")
@ApiModel(value = "SysRoleInheritanceEntity对象", description = "角色继承关系表")
public class SysRoleInheritanceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("祖先角色ID")
    @TableField("ancestor_id")
    private Long ancestorId;

    @ApiModelProperty("后代角色ID")
    @TableField("descendant_id")
    private Long descendantId;

    @ApiModelProperty("继承深度")
    @TableField("depth")
    private Integer depth;
}
