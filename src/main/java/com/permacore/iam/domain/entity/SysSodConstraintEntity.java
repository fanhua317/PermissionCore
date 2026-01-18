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
 * 职责分离约束表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_sod_constraint")
@Schema(name = "SysSodConstraintEntity", description = "职责分离约束表")
public class SysSodConstraintEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "约束ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "约束名称")
    @TableField("constraint_name")
    private String constraintName;

    @Schema(description = "互斥角色ID数组（JSON格式）")
    @TableField("role_set")
    private String roleSet;

    @Schema(description = "约束类型：1-静态互斥 2-动态互斥")
    @TableField("constraint_type")
    private Byte constraintType;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}

/*
 * 非 Lombok 版本示例：
 * public class SysSodConstraintEntity implements Serializable {
 * private static final long serialVersionUID = 1L;
 * private Long id; private String constraintName; private String roleSet;
 * private Byte constraintType; private LocalDateTime createTime;
 *
 * public Long getId() { return id; }
 * public void setId(Long id) { this.id = id; }
 * public String getConstraintName() { return constraintName; }
 * public void setConstraintName(String constraintName) { this.constraintName =
 * constraintName; }
 * public String getRoleSet() { return roleSet; }
 * public void setRoleSet(String roleSet) { this.roleSet = roleSet; }
 * public Byte getConstraintType() { return constraintType; }
 * public void setConstraintType(Byte constraintType) { this.constraintType =
 * constraintType; }
 * public LocalDateTime getCreateTime() { return createTime; }
 * public void setCreateTime(LocalDateTime createTime) { this.createTime =
 * createTime; }
 * }
 */
