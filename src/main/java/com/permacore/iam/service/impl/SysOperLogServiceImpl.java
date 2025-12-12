package com.permacore.iam.service.impl;

import com.permacore.iam.domain.entity.SysOperLogEntity;
import com.permacore.iam.mapper.SysOperLogMapper;
import com.permacore.iam.service.SysOperLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 操作日志表 服务实现类
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Service
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLogEntity> implements SysOperLogService {

}
