package com.permacore.iam.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 权限树形结构VO
 */
@Data
public class PermissionTreeVO {
    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    private String type;  // MENU, BUTTON, API
    private Integer orderNum;
    private Integer status;
    private String remark;
    private List<PermissionTreeVO> children;
}
