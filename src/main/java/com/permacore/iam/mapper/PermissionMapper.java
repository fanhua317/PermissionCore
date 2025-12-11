package com.permacore.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.permacore.iam.domain.entity.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 权限Mapper
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {

    /**
     * 根据用户ID查询所有权限标识
     */
    @Select("SELECT DISTINCT p.perm_key FROM sys_permission p " +
            "WHERE p.status = 1 AND p.id IN (" +
            "  SELECT rp.permission_id FROM sys_role_permission rp " +
            "  WHERE rp.role_id IN (" +
            "    SELECT ur.role_id FROM sys_user_role ur WHERE ur.user_id = #{userId}" +
            "  )" +
            ")")
    Set<String> getUserPermissions(@Param("userId") Long userId);

    /**
     * 根据权限ID列表查询权限标识（XML中实现复杂查询）
     */
    Set<String> selectPermKeysByIds(@Param("ids") Set<Long> ids);

    /**
     * 查询角色的所有权限ID
     */
    @Select("<script>" +
            "SELECT DISTINCT permission_id FROM sys_role_permission " +
            "WHERE role_id IN " +
            "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>" +
            "  #{roleId}" +
            "</foreach>" +
            "</script>")
    Set<Long> selectPermissionIdsByRoleIds(@Param("roleIds") Set<Long> roleIds);

    /**
     * 查询所有API类型的权限
     */
    @Select("SELECT * FROM sys_permission WHERE resource_type = 2 AND status = 1")
    List<PermissionEntity> selectApiPermissions();
}