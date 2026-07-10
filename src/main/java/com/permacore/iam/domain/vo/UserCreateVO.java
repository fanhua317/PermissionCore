package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserCreateVO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在3-20个字符之间")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度需在8-72个字符之间")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @Email(message = "邮箱格式不正确")
    private String email;
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;
    @Size(max = 20, message = "手机号不能超过20个字符")
    private String phone;
    @PositiveOrZero(message = "部门ID不能为负数")
    private Long deptId;
    @Min(value = 0, message = "状态只能是0或1")
    @Max(value = 1, message = "状态只能是0或1")
    private Integer status = 1;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
