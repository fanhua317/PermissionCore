package com.permacore.iam.service;

import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.permacore.iam.domain.vo.SodConstraintVO;

/**
 * <p>
 * 职责分离约束表 服务类
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
public interface SysSodConstraintService extends IService<SysSodConstraintEntity> {

    void createConstraint(SodConstraintVO vo);

    void updateConstraint(Long id, SodConstraintVO vo);

    void deleteConstraint(Long id);
}
