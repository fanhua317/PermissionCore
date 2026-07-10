package com.permacore.iam.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * 分配权限VO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssignPermissionsVO {
    @NotNull(message = "权限列表不能为空")
    @Size(max = 2000, message = "单次最多分配2000个权限")
    private List<@NotNull(message = "权限ID不能为空") @Positive(message = "权限ID必须为正数") Long> permissionIds;
}
