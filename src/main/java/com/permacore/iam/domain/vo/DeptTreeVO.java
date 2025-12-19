package com.permacore.iam.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门树形结构VO
 */
@Data
public class DeptTreeVO {
    private Long id;
    private Long parentId;
    private String deptName;
    private String deptCode;
    private String leader;
    private String phone;
    private Integer orderNum;
    private Integer status;
    private LocalDateTime createTime;
    private Integer userCount;  // 部门用户数量
    private List<DeptTreeVO> children;
}
