package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.vo.SessionRoleStateVO;
import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.SysSodConstraintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleSessionServiceImplTest {

    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private SysRoleInheritanceMapper roleInheritanceMapper;
    @Mock
    private SysSodConstraintService sodConstraintService;

    private RoleSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleSessionServiceImpl(
                userRoleMapper,
                roleMapper,
                rolePermissionMapper,
                permissionMapper,
                roleInheritanceMapper,
                sodConstraintService,
                new ObjectMapper());
    }

    @Test
    void buildStateRejectsDsdConflict() {
        when(userRoleMapper.selectRoleIdsByUserId(7L)).thenReturn(List.of(1L, 2L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, "ROLE_MANAGER", "经理", 1),
                role(2L, "ROLE_HR", "HR", 2)));
        when(roleInheritanceMapper.selectAncestorIdsByDescendantIds(anySet())).thenReturn(Set.of());
        when(sodConstraintService.list(org.mockito.ArgumentMatchers.<Wrapper<SysSodConstraintEntity>>any()))
                .thenReturn(List.of(sod(10L, "经理与HR动态互斥", "[1,2]", (byte) 2)));
        when(roleMapper.selectBatchIds(anySet())).thenReturn(List.of(role(1L, "ROLE_MANAGER", "经理", 1),
                role(2L, "ROLE_HR", "HR", 2)));

        assertThatThrownBy(() -> service.buildState(7L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("动态职责分离");
    }

    @Test
    void buildDefaultStateSkipsDsdConflictRole() {
        when(userRoleMapper.selectRoleIdsByUserId(7L)).thenReturn(List.of(1L, 2L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, "ROLE_MANAGER", "经理", 1),
                role(2L, "ROLE_HR", "HR", 2)));
        when(roleInheritanceMapper.selectAncestorIdsByDescendantIds(anySet())).thenReturn(Set.of());
        when(sodConstraintService.list(org.mockito.ArgumentMatchers.<Wrapper<SysSodConstraintEntity>>any()))
                .thenReturn(List.of(sod(10L, "经理与HR动态互斥", "[1,2]", (byte) 2)));
        when(roleMapper.selectBatchIds(anySet())).thenReturn(List.of(role(1L, "ROLE_MANAGER", "经理", 1),
                role(2L, "ROLE_HR", "HR", 2)));
        when(rolePermissionMapper.selectPermissionIdsByRoleIds(anySet())).thenReturn(Set.of(100L));
        when(permissionMapper.selectPermKeysByIds(anySet())).thenReturn(Set.of("system:user:query"));

        SessionRoleStateVO state = service.buildDefaultState(7L);

        assertThat(state.getActiveRoleIds()).containsExactly(1L);
        assertThat(state.getEffectiveRoleIds()).containsExactly(1L);
        assertThat(state.getPermissions()).containsExactly("system:user:query");
        assertThat(state.getDsdConflicts()).hasSize(1);
    }

    @Test
    void permissionsFollowEffectiveRoleClosure() {
        when(userRoleMapper.selectRoleIdsByUserId(7L)).thenReturn(List.of(3L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3L, "ROLE_DEVELOPER", "开发", 3)));
        when(roleInheritanceMapper.selectAncestorIdsByDescendantIds(anySet())).thenReturn(Set.of(1L));
        when(sodConstraintService.list(org.mockito.ArgumentMatchers.<Wrapper<SysSodConstraintEntity>>any()))
                .thenReturn(List.of());
        when(rolePermissionMapper.selectPermissionIdsByRoleIds(anySet())).thenReturn(Set.of(100L, 101L));
        when(permissionMapper.selectPermKeysByIds(anySet())).thenReturn(Set.of("system:user:query", "system:role:query"));

        SessionRoleStateVO state = service.buildState(7L, List.of(3L));

        assertThat(state.getActiveRoleIds()).containsExactly(3L);
        assertThat(state.getEffectiveRoleIds()).containsExactly(1L, 3L);
        assertThat(state.getPermissions()).containsExactly("system:role:query", "system:user:query");
    }

    private SysRoleEntity role(Long id, String roleKey, String roleName, Integer sortOrder) {
        SysRoleEntity role = new SysRoleEntity();
        role.setId(id);
        role.setRoleKey(roleKey);
        role.setRoleName(roleName);
        role.setSortOrder(sortOrder);
        role.setStatus((byte) 1);
        role.setDelFlag((byte) 0);
        return role;
    }

    private SysSodConstraintEntity sod(Long id, String name, String roleSet, Byte type) {
        SysSodConstraintEntity constraint = new SysSodConstraintEntity();
        constraint.setId(id);
        constraint.setConstraintName(name);
        constraint.setRoleSet(roleSet);
        constraint.setConstraintType(type);
        return constraint;
    }
}
