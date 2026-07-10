package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysRoleInheritanceEntity;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.entity.SysUserRoleEntity;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.SysSodConstraintService;
import com.permacore.iam.service.AuthorizationStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysRoleInheritanceServiceImplTest {

    @Mock
    private SysRoleInheritanceMapper roleInheritanceMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysSodConstraintService sodConstraintService;
    @Mock
    private AuthorizationStateService authorizationStateService;

    private SysRoleInheritanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysRoleInheritanceServiceImpl(
                roleInheritanceMapper,
                roleMapper,
                userRoleMapper,
                userMapper,
                sodConstraintService,
                new ObjectMapper(),
                authorizationStateService);
    }

    @Test
    void updateParentRolesRejectsSelfInheritance() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L));

        assertThatThrownBy(() -> service.updateParentRoles(1L, List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能继承自身");

        verify(roleInheritanceMapper, never()).delete(any());
        verify(roleInheritanceMapper, never()).insert(any(SysRoleInheritanceEntity.class));
    }

    @Test
    void updateParentRolesRejectsCycle() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role(2L)));
        when(roleInheritanceMapper.selectList(any())).thenReturn(List.of(edge(1L, 2L)));

        assertThatThrownBy(() -> service.updateParentRoles(1L, List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("形成环");

        verify(roleInheritanceMapper, never()).delete(any());
        verify(roleInheritanceMapper, never()).insert(any(SysRoleInheritanceEntity.class));
    }

    @Test
    void updateParentRolesRejectsExistingUserSsdConflict() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role(2L)));
        when(roleInheritanceMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<SysUserEntity>>any()))
                .thenReturn(List.of(user(9L)));
        when(userRoleMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<SysUserRoleEntity>>any()))
                .thenReturn(List.of(userRole(9L, 1L)));
        when(sodConstraintService.list(org.mockito.ArgumentMatchers.<Wrapper<SysSodConstraintEntity>>any()))
                .thenReturn(List.of(sod("开发与审计互斥", "[1,2]")));

        assertThatThrownBy(() -> service.updateParentRoles(1L, List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户ID=9")
                .hasMessageContaining("开发与审计互斥")
                .hasMessageContaining("冲突角色ID=[1, 2]");

        verify(roleInheritanceMapper, never()).delete(any());
        verify(roleInheritanceMapper, never()).insert(any(SysRoleInheritanceEntity.class));
    }

    @Test
    void updateParentRolesSavesDistinctMultipleParentsWhenLegal() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role(2L), role(3L)));
        when(roleInheritanceMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<SysUserEntity>>any()))
                .thenReturn(List.of());

        service.updateParentRoles(1L, List.of(2L, 2L, 3L));

        ArgumentCaptor<SysRoleInheritanceEntity> captor = ArgumentCaptor.forClass(SysRoleInheritanceEntity.class);
        verify(roleInheritanceMapper).delete(any());
        verify(roleInheritanceMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SysRoleInheritanceEntity::getAncestorId)
                .containsExactly(2L, 3L);
        assertThat(captor.getAllValues())
                .allSatisfy(edge -> {
                    assertThat(edge.getDescendantId()).isEqualTo(1L);
                    assertThat(edge.getDepth()).isEqualTo(1);
                });
    }

    private SysRoleEntity role(Long id) {
        SysRoleEntity role = new SysRoleEntity();
        role.setId(id);
        role.setRoleName("role-" + id);
        role.setDelFlag((byte) 0);
        return role;
    }

    private SysRoleInheritanceEntity edge(Long ancestorId, Long descendantId) {
        SysRoleInheritanceEntity edge = new SysRoleInheritanceEntity();
        edge.setAncestorId(ancestorId);
        edge.setDescendantId(descendantId);
        edge.setDepth(1);
        return edge;
    }

    private SysUserEntity user(Long id) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setDelFlag((byte) 0);
        return user;
    }

    private SysUserRoleEntity userRole(Long userId, Long roleId) {
        SysUserRoleEntity userRole = new SysUserRoleEntity();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }

    private SysSodConstraintEntity sod(String name, String roleSet) {
        SysSodConstraintEntity constraint = new SysSodConstraintEntity();
        constraint.setConstraintName(name);
        constraint.setRoleSet(roleSet);
        constraint.setConstraintType((byte) 1);
        return constraint;
    }
}
