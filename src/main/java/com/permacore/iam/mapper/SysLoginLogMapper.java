package com.permacore.iam.mapper;

import com.permacore.iam.domain.entity.SysLoginLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 登录日志表 Mapper 接口
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLogEntity> {

}
