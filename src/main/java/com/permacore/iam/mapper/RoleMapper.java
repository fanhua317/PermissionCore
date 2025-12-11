package com.permacore.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.permacore.iam.domain.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 角色Mapper
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    /**
     * 查询角色的所有后代角色ID（递归查询，用于权限传播）
     */
    Set<Long> getDescendantRoleIds(@Param("roleId") Long roleId);

    /**
     * 查询角色的所有祖先角色ID（用于权限聚合）
     */
    Set<Long> getAncestorRoleIds(@Param("roleId") Long roleId);

    /**
     * 查询角色的直接子角色
     */
    @Select("SELECT * FROM sys_role WHERE parent_id = #{parentId} AND del_flag = 0 ORDER BY sort_order")
    List<RoleEntity> selectChildrenByParentId(@Param("parentId") Long parentId);
}