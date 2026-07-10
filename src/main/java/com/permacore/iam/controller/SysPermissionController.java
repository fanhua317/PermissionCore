package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.permacore.iam.domain.entity.SysPermissionEntity;
import com.permacore.iam.domain.vo.PermissionTreeVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.PermissionUpsertVO;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.service.SysPermissionService;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.domain.vo.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;

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
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysRoleMapper roleMapper;
    private final AuthorizationStateService authorizationStateService;

    /**
     * 获取权限树形结构
     */
    @Operation(summary = "获取权限树", description = "获取所有权限的树形结构")
    @PreAuthorize("hasAnyAuthority('system:permission:query','role:assignPermission')")
    @GetMapping("/tree")
    public Result<List<PermissionTreeVO>> tree() {
        List<SysPermissionEntity> allPerms = permissionService.list(
            new LambdaQueryWrapper<SysPermissionEntity>()
                .orderByAsc(SysPermissionEntity::getSortOrder)
        );
        
        List<PermissionTreeVO> tree = buildPermTree(allPerms, 0L, new HashSet<>());
        return Result.success(tree);
    }

    /**
     * 获取权限列表（平铺）
     */
    @Operation(summary = "获取权限列表", description = "获取所有权限列表（平铺）")
    @PreAuthorize("hasAnyAuthority('system:permission:query','role:assignPermission')")
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
    @PreAuthorize("hasAnyAuthority('system:permission:query','role:assignPermission')")
    @GetMapping("/{id}")
    public Result<SysPermissionEntity> getById(@PathVariable Long id) {
        SysPermissionEntity perm = permissionService.getById(id);
        if (perm == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限不存在");
        }
        return Result.success(perm);
    }

    /**
     * 创建权限
     */
    @Operation(summary = "创建权限", description = "新增权限")
    @PreAuthorize("hasAuthority('permission:add')")
    @PostMapping
    @Transactional
    public Result<Void> create(@Valid @RequestBody PermissionUpsertVO vo) {
        roleMapper.lockAllRoleIds();
        validatePermissionFields(vo, true);
        Long parentId = vo.getParentId() == null ? 0L : vo.getParentId();
        validateParent(parentId, null);
        SysPermissionEntity permission = new SysPermissionEntity();
        applyPermissionFields(permission, vo);
        permission.setParentId(parentId);
        permission.setSortOrder(vo.getOrderNum() == null ? 0 : vo.getOrderNum());
        permission.setStatus(vo.getStatus() == null ? (byte) 1 : vo.getStatus().byteValue());
        if (!permissionService.save(permission)) {
            throw new BusinessException(ResultCode.ERROR, "创建权限失败");
        }
        authorizationStateService.invalidateAllUsers();
        log.info("创建权限: {}", permission.getPermName());
        return Result.success();
    }

    /**
     * 更新权限
     */
    @Operation(summary = "更新权限", description = "更新权限信息")
    @PreAuthorize("hasAuthority('permission:edit')")
    @PutMapping("/{id}")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PermissionUpsertVO vo) {
        roleMapper.lockAllRoleIds();
        SysPermissionEntity permission = permissionService.getById(id);
        if (permission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限不存在");
        }
        validatePermissionFields(vo, false);
        if (vo.getParentId() != null) {
            validateParent(vo.getParentId(), id);
        }
        applyPermissionFields(permission, vo);
        if (!permissionService.updateById(permission)) {
            throw new BusinessException(ResultCode.ERROR, "更新权限失败");
        }
        authorizationStateService.invalidateAllUsers();
        log.info("更新权限: permId={}", id);
        return Result.success();
    }

    /**
     * 删除权限（级联删除子权限）
     */
    @Operation(summary = "删除权限", description = "删除权限及其子权限")
    @PreAuthorize("hasAuthority('permission:delete')")
    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        roleMapper.lockAllRoleIds();
        Set<Long> permissionIds = collectPermissionTreeIds(id);
        if (permissionIds.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限不存在");
        }
        rolePermissionMapper.deleteByPermissionIds(permissionIds);
        permissionService.removeByIds(permissionIds);
        authorizationStateService.invalidateAllUsers();
        log.info("删除权限: permId={}", id);
        return Result.success();
    }

    /**
     * 递归删除子权限
     */
    private List<PermissionTreeVO> buildPermTree(List<SysPermissionEntity> perms, Long parentId, Set<Long> visited) {
        return perms.stream()
            .filter(p -> parentId.equals(p.getParentId()) && p.getId() != null && !visited.contains(p.getId()))
            .map(p -> {
                visited.add(p.getId());
                PermissionTreeVO vo = new PermissionTreeVO();
                vo.setId(p.getId());
                vo.setParentId(p.getParentId());
                vo.setPermCode(p.getPermKey());
                vo.setPermName(p.getPermName());
                vo.setType(convertResourceType(p.getResourceType()));
                vo.setOrderNum(p.getSortOrder());
                vo.setStatus(p.getStatus() != null ? p.getStatus().intValue() : 1);
                vo.setChildren(buildPermTree(perms, p.getId(), visited));
                return vo;
            })
            .collect(Collectors.toList());
    }

    private void applyPermissionFields(SysPermissionEntity permission, PermissionUpsertVO vo) {
        if (vo.getPermCode() != null) {
            permission.setPermKey(vo.getPermCode().trim());
        }
        if (vo.getPermName() != null) {
            permission.setPermName(vo.getPermName().trim());
        }
        if (vo.getParentId() != null) {
            permission.setParentId(vo.getParentId());
        }
        if (vo.getType() != null) {
            permission.setResourceType(convertTypeToResourceType(vo.getType()));
        }
        if (vo.getOrderNum() != null) {
            permission.setSortOrder(vo.getOrderNum());
        }
        if (vo.getStatus() != null) {
            permission.setStatus(vo.getStatus().byteValue());
        }
    }

    private void validatePermissionFields(PermissionUpsertVO vo, boolean required) {
        if (required && (vo.getPermCode() == null || vo.getPermName() == null || vo.getType() == null)) {
            throw new BusinessException("权限标识、名称和资源类型不能为空");
        }
        if (vo.getPermCode() != null && vo.getPermCode().isBlank()) {
            throw new BusinessException("权限标识不能为空");
        }
        if (vo.getPermName() != null && vo.getPermName().isBlank()) {
            throw new BusinessException("权限名称不能为空");
        }
    }

    private void validateParent(Long parentId, Long currentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId.equals(currentId) || permissionService.getById(parentId) == null) {
            throw new com.permacore.iam.security.handler.BusinessException("上级权限不存在或不能选择自身");
        }
        Map<Long, Long> parents = permissionService.list().stream()
                .filter(permission -> permission.getId() != null)
                .collect(Collectors.toMap(SysPermissionEntity::getId, SysPermissionEntity::getParentId, (a, b) -> a));
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L && visited.add(cursor)) {
            if (cursor.equals(currentId)) {
                throw new com.permacore.iam.security.handler.BusinessException("上级权限不能是当前权限的子权限");
            }
            cursor = parents.get(cursor);
        }
        if (cursor != null && cursor != 0L) {
            throw new com.permacore.iam.security.handler.BusinessException("权限树中存在环，请先修复数据");
        }
    }

    private Set<Long> collectPermissionTreeIds(Long rootId) {
        List<SysPermissionEntity> all = permissionService.list();
        Map<Long, List<Long>> children = all.stream()
                .filter(permission -> permission.getId() != null && permission.getParentId() != null)
                .collect(Collectors.groupingBy(SysPermissionEntity::getParentId,
                        Collectors.mapping(SysPermissionEntity::getId, Collectors.toList())));
        Set<Long> existingIds = all.stream().map(SysPermissionEntity::getId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (!existingIds.contains(rootId)) {
            return Set.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (result.add(current)) {
                queue.addAll(children.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    /**
     * 转换资源类型
     */
    private String convertResourceType(Byte type) {
        if (type == null) return "MENU";
        switch (type) {
            case 1: return "MENU";
            case 2: return "BUTTON";
            case 3: return "API";
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
            case "BUTTON": return (byte) 2;
            case "API": return (byte) 3;
            default: return (byte) 1;
        }
    }
}
