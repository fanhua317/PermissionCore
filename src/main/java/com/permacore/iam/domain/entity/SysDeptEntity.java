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
 * 部门表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_dept")
@ApiModel(value = "SysDeptEntity对象", description = "部门表")
public class SysDeptEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("部门ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("父部门ID")
    @TableField("parent_id")
    private Long parentId;

    @ApiModelProperty("部门名称")
    @TableField("dept_name")
    private String deptName;

    @ApiModelProperty("部门路径（如：/1/2/3）")
    @TableField("dept_path")
    private String deptPath;

    @ApiModelProperty("负责人ID")
    @TableField("leader_id")
    private Long leaderId;

    @ApiModelProperty("联系电话")
    @TableField("phone")
    private String phone;

    @ApiModelProperty("邮箱")
    @TableField("email")
    private String email;

    @ApiModelProperty("排序")
    @TableField("sort_order")
    private Integer sortOrder;

    @ApiModelProperty("状态：1-正常 0-停用")
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

    @ApiModelProperty("删除标志")
    @TableField("del_flag")
    @TableLogic
    private Byte delFlag;
}
