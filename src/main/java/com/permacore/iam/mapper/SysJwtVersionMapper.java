package com.permacore.iam.mapper;

import com.permacore.iam.domain.entity.SysJwtVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * JWT版本控制表 Mapper 接口
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Mapper
public interface SysJwtVersionMapper extends BaseMapper<SysJwtVersionEntity> {

}
