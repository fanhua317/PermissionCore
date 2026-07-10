package com.permacore.iam.mapper;

import com.permacore.iam.domain.entity.SysUserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {
    int incrementAuthVersions(@Param("userIds") Collection<Long> userIds);

    int incrementAllActiveAuthVersions();

    SysUserEntity selectAuthorizationStateById(@Param("userId") Long userId);
}
