package com.permacore.iam.mapper;

import com.permacore.iam.domain.entity.SysRoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>
 * 角色表 Mapper 接口
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRoleEntity> {

    /** Serialize authorization graph mutations within the current transaction. */
    List<Long> lockAllRoleIds();

    /** Keep token issuance concurrent while excluding authorization graph mutations. */
    List<Long> lockAllRoleIdsShared();
}
