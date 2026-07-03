package com.permacore.iam.domain.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class SessionRoleStateVO {
    private List<SessionRoleVO> roles = new ArrayList<>();
    private List<Long> activeRoleIds = new ArrayList<>();
    private List<Long> effectiveRoleIds = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<DsdConflictVO> dsdConflicts = new ArrayList<>();
}
