package com.permacore.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.permacore.iam.domain.entity.SysRolePermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 角色权限关联表 Mapper 接口
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermissionEntity> {

    Set<Long> selectPermissionIdsByRoleIds(@Param("roleIds") Set<Long> roleIds);

    void deleteByRoleId(@Param("roleId") Long roleId);

    void insertBatch(@Param("records") List<SysRolePermissionEntity> records);
}
