package com.permacore.iam.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 分配角色VO
 */
@Data
public class AssignRolesVO {
    private List<Long> roleIds;
}
