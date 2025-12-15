package com.permacore.iam.mapper;

import com.permacore.iam.domain.entity.SysRoleInheritanceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

/**
 * <p>
 * 角色继承关系表 Mapper 接口
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Mapper
public interface SysRoleInheritanceMapper extends BaseMapper<SysRoleInheritanceEntity> {

    Set<Long> selectAncestorIdsByDescendantId(@Param("descendantId") Long descendantId);
}
