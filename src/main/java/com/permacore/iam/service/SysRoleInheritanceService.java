package com.permacore.iam.service;

import com.permacore.iam.domain.entity.SysRoleInheritanceEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 角色继承关系表 服务类
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
public interface SysRoleInheritanceService extends IService<SysRoleInheritanceEntity> {

    void updateParentRoles(Long roleId, List<Long> parentRoleIds);

    void addInheritance(Long childId, Long parentId);

    void removeInheritance(Long childId, Long parentId);
}
