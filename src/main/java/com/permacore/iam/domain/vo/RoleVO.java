package com.permacore.iam.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 角色VO
 */
@Data
public class RoleVO {
    private Long id;
    private String roleCode;
    private String roleName;
}
