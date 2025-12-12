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
 * 职责分离约束表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_sod_constraint")
@ApiModel(value = "SysSodConstraintEntity对象", description = "职责分离约束表")
public class SysSodConstraintEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("约束ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("约束名称")
    @TableField("constraint_name")
    private String constraintName;

    @ApiModelProperty("互斥角色ID数组（JSON格式）")
    @TableField("role_set")
    private String roleSet;

    @ApiModelProperty("约束类型：1-静态互斥 2-动态互斥")
    @TableField("constraint_type")
    private Byte constraintType;

    @ApiModelProperty("创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}
