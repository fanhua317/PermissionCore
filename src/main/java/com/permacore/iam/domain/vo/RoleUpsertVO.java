package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleUpsertVO {
    @NotBlank(message = "角色标识不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,99}$", message = "角色标识只能包含大写字母、数字和下划线")
    private String roleKey;
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称不能超过100个字符")
    private String roleName;
    @Size(max = 500, message = "角色描述不能超过500个字符")
    private String remark;
    @Min(value = 0, message = "状态只能是0或1")
    @Max(value = 1, message = "状态只能是0或1")
    private Integer status = 1;
    private Integer sortOrder = 0;
}
