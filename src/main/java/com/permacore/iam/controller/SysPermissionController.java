package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.permacore.iam.domain.entity.SysPermissionEntity;
import com.permacore.iam.domain.vo.PermissionTreeVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限管理控制器
 */
@Tag(name = "权限管理", description = "菜单与权限点管理")
@RestController
@RequestMapping("/api/permission")
@RequiredArgsConstructor
public class SysPermissionController {

    private static final Logger log = LoggerFactory.getLogger(SysPermissionController.class);

    private final SysPermissionService permissionService;

    /**
     * 获取权限树形结构
     */
    @Operation(summary = "获取权限树", description = "获取所有权限的树形结构")
    @GetMapping("/tree")
    public Result<List<PermissionTreeVO>> tree() {
        List<SysPermissionEntity> allPerms = permissionService.list(
            new LambdaQueryWrapper<SysPermissionEntity>()
                .orderByAsc(SysPermissionEntity::getSortOrder)
        );
        
        List<PermissionTreeVO> tree = buildPermTree(allPerms, 0L);
        return Result.success(tree);
    }

    /**
     * 获取权限列表（平铺）
     */
    @Operation(summary = "获取权限列表", description = "获取所有权限列表（平铺）")
    @GetMapping("/list")
    public Result<List<SysPermissionEntity>> list() {
        List<SysPermissionEntity> perms = permissionService.list(
            new LambdaQueryWrapper<SysPermissionEntity>()
                .orderByAsc(SysPermissionEntity::getSortOrder)
        );
        return Result.success(perms);
    }

    /**
     * 获取权限详情
     */
    @Operation(summary = "获取权限详情", description = "根据ID获取权限详情")
    @GetMapping("/{id}")
    public Result<SysPermissionEntity> getById(@PathVariable Long id) {
        SysPermissionEntity perm = permissionService.getById(id);
        return Result.success(perm);
    }

    /**
     * 创建权限
     */
    @Operation(summary = "创建权限", description = "新增权限")
    @PreAuthorize("hasAuthority('permission:add')")
    @PostMapping
    public Result<Void> create(@RequestBody java.util.Map<String, Object> permissionData) {
        SysPermissionEntity permission = new SysPermissionEntity();
        
        // 处理前端传入的字段映射
        if (permissionData.get("permCode") != null) {
            permission.setPermKey(permissionData.get("permCode").toString());
        }
        if (permissionData.get("permName") != null) {
            permission.setPermName(permissionData.get("permName").toString());
        }
        if (permissionData.get("parentId") != null) {
            Object parentId = permissionData.get("parentId");
            permission.setParentId(parentId instanceof Number ? ((Number) parentId).longValue() : Long.parseLong(parentId.toString()));
        } else {
            permission.setParentId(0L);
        }
        if (permissionData.get("type") != null) {
            permission.setResourceType(convertTypeToResourceType(permissionData.get("type").toString()));
        }
        if (permissionData.get("orderNum") != null) {
            Object orderNum = permissionData.get("orderNum");
            permission.setSortOrder(orderNum instanceof Number ? ((Number) orderNum).intValue() : Integer.parseInt(orderNum.toString()));
        } else {
            permission.setSortOrder(0);
        }
        if (permissionData.get("status") != null) {
            Object status = permissionData.get("status");
            permission.setStatus(status instanceof Number ? ((Number) status).byteValue() : Byte.parseByte(status.toString()));
        } else {
            permission.setStatus((byte) 1);
        }
        
        permissionService.save(permission);
        log.info("创建权限: {}", permission.getPermName());
        return Result.success();
    }

    /**
     * 更新权限
     */
    @Operation(summary = "更新权限", description = "更新权限信息")
    @PreAuthorize("hasAuthority('permission:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody java.util.Map<String, Object> permissionData) {
        SysPermissionEntity permission = permissionService.getById(id);
        if (permission == null) {
            return Result.error("权限不存在");
        }
        
        // 处理前端传入的字段映射
        if (permissionData.get("permCode") != null) {
            permission.setPermKey(permissionData.get("permCode").toString());
        }
        if (permissionData.get("permName") != null) {
            permission.setPermName(permissionData.get("permName").toString());
        }
        if (permissionData.containsKey("parentId")) {
            Object parentId = permissionData.get("parentId");
            if (parentId != null) {
                permission.setParentId(parentId instanceof Number ? ((Number) parentId).longValue() : Long.parseLong(parentId.toString()));
            } else {
                permission.setParentId(0L);
            }
        }
        if (permissionData.get("type") != null) {
            permission.setResourceType(convertTypeToResourceType(permissionData.get("type").toString()));
        }
        if (permissionData.get("orderNum") != null) {
            Object orderNum = permissionData.get("orderNum");
            permission.setSortOrder(orderNum instanceof Number ? ((Number) orderNum).intValue() : Integer.parseInt(orderNum.toString()));
        }
        if (permissionData.get("status") != null) {
            Object status = permissionData.get("status");
            permission.setStatus(status instanceof Number ? ((Number) status).byteValue() : Byte.parseByte(status.toString()));
        }
        
        permissionService.updateById(permission);
        log.info("更新权限: permId={}", id);
        return Result.success();
    }

    /**
     * 删除权限（级联删除子权限）
     */
    @Operation(summary = "删除权限", description = "删除权限及其子权限")
    @PreAuthorize("hasAuthority('permission:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 删除子权限
        deleteChildPermissions(id);
        // 删除本权限
        permissionService.removeById(id);
        log.info("删除权限: permId={}", id);
        return Result.success();
    }

    /**
     * 递归删除子权限
     */
    private void deleteChildPermissions(Long parentId) {
        List<SysPermissionEntity> children = permissionService.list(
            new LambdaQueryWrapper<SysPermissionEntity>()
                .eq(SysPermissionEntity::getParentId, parentId)
        );
        for (SysPermissionEntity child : children) {
            deleteChildPermissions(child.getId());
            permissionService.removeById(child.getId());
        }
    }

    /**
     * 构建权限树
     */
    private List<PermissionTreeVO> buildPermTree(List<SysPermissionEntity> perms, Long parentId) {
        return perms.stream()
            .filter(p -> parentId.equals(p.getParentId()))
            .map(p -> {
                PermissionTreeVO vo = new PermissionTreeVO();
                vo.setId(p.getId());
                vo.setParentId(p.getParentId());
                vo.setPermCode(p.getPermKey());
                vo.setPermName(p.getPermName());
                vo.setType(convertResourceType(p.getResourceType()));
                vo.setOrderNum(p.getSortOrder());
                vo.setStatus(p.getStatus() != null ? p.getStatus().intValue() : 1);
                vo.setChildren(buildPermTree(perms, p.getId()));
                return vo;
            })
            .collect(Collectors.toList());
    }

    /**
     * 转换资源类型
     */
    private String convertResourceType(Byte type) {
        if (type == null) return "MENU";
        switch (type) {
            case 1: return "MENU";
            case 2: return "API";
            case 3: return "BUTTON";
            default: return "MENU";
        }
    }

    /**
     * 将前端类型字符串转换为数据库resourceType
     */
    private Byte convertTypeToResourceType(String type) {
        if (type == null) return (byte) 1;
        switch (type.toUpperCase()) {
            case "MENU": return (byte) 1;
            case "API": return (byte) 2;
            case "BUTTON": return (byte) 3;
            default: return (byte) 1;
        }
    }
}
