package com.permacore.iam.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 分配权限VO
 */
@Data
public class AssignPermissionsVO {
    private List<Long> permissionIds;
}
