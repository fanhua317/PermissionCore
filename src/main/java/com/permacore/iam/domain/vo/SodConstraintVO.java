package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SodConstraintVO {
    @NotBlank(message = "约束名称不能为空")
    @Size(max = 200, message = "约束名称不能超过200个字符")
    private String constraintName;
    @NotBlank(message = "互斥角色不能为空")
    private String roleSet;
    @Min(value = 1, message = "约束类型只能是1或2")
    @Max(value = 2, message = "约束类型只能是1或2")
    @NotNull(message = "约束类型不能为空")
    private Byte constraintType;
}
