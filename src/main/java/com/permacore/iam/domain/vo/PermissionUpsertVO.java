package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PermissionUpsertVO {
    @Size(max = 100, message = "权限标识不能超过100个字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*(?::(?:[A-Za-z][A-Za-z0-9_]*|\\*))*$",
            message = "权限标识格式不正确")
    private String permCode;
    @Size(max = 100, message = "权限名称不能超过100个字符")
    private String permName;
    private Long parentId;
    @Pattern(regexp = "(?i)MENU|BUTTON|API", message = "资源类型只能是MENU、BUTTON或API")
    private String type;
    private Integer orderNum;
    @Min(value = 0, message = "状态只能是0或1")
    @Max(value = 1, message = "状态只能是0或1")
    private Integer status;
}
