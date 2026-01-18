package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "SysRoleInheritanceEntity", description = "角色继承关系表")
public class SysRoleInheritanceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "祖先角色ID")
    @TableField("ancestor_id")
    private Long ancestorId;

    @Schema(description = "后代角色ID")
    @TableField("descendant_id")
    private Long descendantId;

    @Schema(description = "继承深度")
    @TableField("depth")
    private Integer depth;
}

/*
 * 非 Lombok 版本示例：
 * public class SysRoleInheritanceEntity implements Serializable {
 * private static final long serialVersionUID = 1L;
 * private Long id; private Long ancestorId; private Long descendantId; private
 * Integer depth;
 *
 * public Long getId() { return id; }
 * public void setId(Long id) { this.id = id; }
 * public Long getAncestorId() { return ancestorId; }
 * public void setAncestorId(Long ancestorId) { this.ancestorId = ancestorId; }
 * public Long getDescendantId() { return descendantId; }
 * public void setDescendantId(Long descendantId) { this.descendantId =
 * descendantId; }
 * public Integer getDepth() { return depth; }
 * public void setDepth(Integer depth) { this.depth = depth; }
 * }
 */
