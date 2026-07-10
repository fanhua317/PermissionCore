package com.permacore.iam.mapper;

import com.permacore.iam.domain.entity.SysUserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;
import java.util.List;

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

    int incrementGlobalAuthVersion();

    SysUserEntity selectAuthorizationStateById(@Param("userId") Long userId);

    SysUserEntity selectAuthenticationStateById(@Param("userId") Long userId);

    SysUserEntity selectAuthenticationStateByUsername(@Param("username") String username);

    /** Lock one active user so concurrent mutations of different users stay independent. */
    SysUserEntity selectByIdForUpdate(@Param("userId") Long userId);

    long countUserPage(@Param("username") String username,
                       @Param("nickname") String nickname,
                       @Param("sharedKeyword") boolean sharedKeyword,
                       @Param("status") Integer status,
                       @Param("deptId") Long deptId);

    List<SysUserEntity> selectUserPage(@Param("offset") long offset,
                                       @Param("pageSize") int pageSize,
                                       @Param("username") String username,
                                       @Param("nickname") String nickname,
                                       @Param("sharedKeyword") boolean sharedKeyword,
                                       @Param("status") Integer status,
                                       @Param("deptId") Long deptId);
}
