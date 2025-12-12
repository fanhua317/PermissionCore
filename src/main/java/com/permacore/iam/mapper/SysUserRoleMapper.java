package com.permacore.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.permacore.iam.domain.entity.SysUserRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户角色关联表 Mapper 接口
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRoleEntity> {

    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    void deleteByUserId(@Param("userId") Long userId);
}
