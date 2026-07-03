package com.permacore.iam.domain.vo;

import java.util.List;

import lombok.Data;

@Data
public class DsdConflictVO {
    private Long constraintId;
    private String constraintName;
    private List<Long> roleIds;
    private List<String> roleNames;
}
