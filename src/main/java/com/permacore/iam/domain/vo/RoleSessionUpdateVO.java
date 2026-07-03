package com.permacore.iam.domain.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class RoleSessionUpdateVO {
    private List<Long> activeRoleIds = new ArrayList<>();
}
