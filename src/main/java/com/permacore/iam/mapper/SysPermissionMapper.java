package com.permacore.iam.mapper;

import com.permacore.iam.domain.entity.SysPermissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

/**
 * <p>
 * 权限表 Mapper 接口
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermissionEntity> {

    /**
     * 根据权限ID集合查询权限标识
     */
    @Select("<script>" +
            "SELECT perm_key FROM sys_permission WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND status = 1" +
            "</script>")
    Set<String> selectPermKeysByIds(@Param("ids") Set<Long> ids);

    /**
     * 查询全部启用权限标识，用于展开 admin:* 通配权限
     */
    @Select("SELECT perm_key FROM sys_permission " +
            "WHERE status = 1 AND perm_key IS NOT NULL AND TRIM(perm_key) <> ''")
    Set<String> selectAllEnabledPermKeys();
}
