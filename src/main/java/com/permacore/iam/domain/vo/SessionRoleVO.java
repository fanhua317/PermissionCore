package com.permacore.iam.domain.vo;

import lombok.Data;

@Data
public class SessionRoleVO {
    private Long id;
    private String roleKey;
    private String roleName;
    private Integer sortOrder;
    private Boolean active;
    private Boolean effective;
}
