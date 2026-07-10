package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordVO {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 72, message = "新密码长度需在8-72个字符之间")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String newPassword;
}
