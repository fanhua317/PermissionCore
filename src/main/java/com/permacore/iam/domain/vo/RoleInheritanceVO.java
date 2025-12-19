package com.permacore.iam.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 角色继承配置VO
 */
@Data
public class RoleInheritanceVO {
    private List<Long> parentRoleIds;
}
