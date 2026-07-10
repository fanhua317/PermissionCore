package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUpdateVO {
    @Pattern(regexp = "(?s).*\\S.*", message = "昵称不能为空")
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;
    @Email(message = "邮箱格式不正确")
    private String email;
    @Size(max = 20, message = "手机号不能超过20个字符")
    private String phone;
    @PositiveOrZero(message = "部门ID不能为负数")
    private Long deptId;
    @Min(value = 0, message = "状态只能是0或1")
    @Max(value = 1, message = "状态只能是0或1")
    private Integer status;
}
