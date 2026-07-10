package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.SysSodConstraintService;
import com.permacore.iam.service.AuthorizationStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplSsdTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysSodConstraintService sodConstraintService;
    @Mock
    private AuthorizationStateService authorizationStateService;

    private UserServiceImpl service;
    private FakePermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new FakePermissionService();
        service = new UserServiceImpl(
                userMapper,
                userRoleMapper,
                roleMapper,
                permissionService,
                sodConstraintService,
                new ObjectMapper(),
                authorizationStateService);
        SysUserEntity user = new SysUserEntity();
        user.setId(9L);
        user.setDelFlag((byte) 0);
        when(userMapper.selectById(9L)).thenReturn(user);
        when(roleMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) invocation.getArgument(0);
            return ids.stream().map(this::enabledRole).toList();
        });
    }

    @Test
    void assignRolesRejectsDirectSsdConflict() {
        permissionService.roleIdsWithInheritance = Set.of(1L, 2L);
        when(sodConstraintService.list(org.mockito.ArgumentMatchers.<Wrapper<SysSodConstraintEntity>>any()))
                .thenReturn(List.of(sod("审计员与开发人员互斥", "[1,2]")));

        assertThatThrownBy(() -> service.assignRoles(9L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessageContaining("职责分离");
        verify(userRoleMapper, never()).deleteByUserId(9L);
    }

    @Test
    void assignRolesRejectsInheritedSsdConflict() {
        permissionService.roleIdsWithInheritance = Set.of(1L, 3L);
        when(sodConstraintService.list(org.mockito.ArgumentMatchers.<Wrapper<SysSodConstraintEntity>>any()))
                .thenReturn(List.of(sod("继承互斥", "[1,3]")));

        assertThatThrownBy(() -> service.assignRoles(9L, List.of(3L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessageContaining("职责分离");
        verify(userRoleMapper, never()).deleteByUserId(9L);
    }

    private SysSodConstraintEntity sod(String name, String roleSet) {
        SysSodConstraintEntity constraint = new SysSodConstraintEntity();
        constraint.setConstraintName(name);
        constraint.setRoleSet(roleSet);
        constraint.setConstraintType((byte) 1);
        return constraint;
    }

    private SysRoleEntity enabledRole(Long id) {
        SysRoleEntity role = new SysRoleEntity();
        role.setId(id);
        role.setStatus((byte) 1);
        role.setDelFlag((byte) 0);
        return role;
    }

    private static class FakePermissionService extends PermissionService {
        private Set<Long> roleIdsWithInheritance = Set.of();

        FakePermissionService() {
            super(null, null, null);
        }

        @Override
        public Set<Long> getPotentialRoleIdsWithInheritance(java.util.Collection<Long> directRoleIds) {
            return roleIdsWithInheritance;
        }
    }
}
