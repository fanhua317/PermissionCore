package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeptUpsertVO {
    private Long parentId;
    @Size(max = 100, message = "部门名称不能超过100个字符")
    private String deptName;
    private Long leaderId;
    @Size(max = 20, message = "联系电话不能超过20个字符")
    private String phone;
    @Email(message = "邮箱格式不正确")
    private String email;
    private Integer sortOrder;
    @Min(value = 0, message = "状态只能是0或1")
    @Max(value = 1, message = "状态只能是0或1")
    private Integer status;
}
