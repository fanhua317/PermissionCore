package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * 分配角色VO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssignRolesVO {
    @NotNull(message = "角色列表不能为空")
    @Size(max = 500, message = "单次最多分配500个角色")
    private List<@NotNull(message = "角色ID不能为空") @Positive(message = "角色ID必须为正数") Long> roleIds;
}
