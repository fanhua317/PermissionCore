package com.permacore.iam.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.service.SysSodConstraintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplLockingTest {

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

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(
                userMapper,
                userRoleMapper,
                roleMapper,
                new PermissionService(null, null, null),
                sodConstraintService,
                new ObjectMapper(),
                authorizationStateService
        );
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
    }

    @Test
    void assignmentsForDifferentUsersShareTheGraphAndLockOnlyTheirTargetRows() {
        when(userMapper.selectByIdForUpdate(9L)).thenReturn(activeUser(9L));
        when(userMapper.selectByIdForUpdate(10L)).thenReturn(activeUser(10L));

        service.assignRoles(9L, List.of());
        service.assignRoles(10L, List.of());

        InOrder order = inOrder(roleMapper, userMapper, userRoleMapper, authorizationStateService);
        order.verify(roleMapper).lockAllRoleIdsShared();
        order.verify(userMapper).selectByIdForUpdate(9L);
        order.verify(userRoleMapper).deleteByUserId(9L);
        order.verify(authorizationStateService).invalidateUsers(List.of(9L));
        order.verify(roleMapper).lockAllRoleIdsShared();
        order.verify(userMapper).selectByIdForUpdate(10L);
        order.verify(userRoleMapper).deleteByUserId(10L);
        order.verify(authorizationStateService).invalidateUsers(List.of(10L));
        verify(roleMapper, never()).lockAllRoleIds();
    }

    @Test
    void missingUserStillFailsBeforeAnyRoleRelationshipMutation() {
        when(userMapper.selectByIdForUpdate(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.assignRoles(404L, List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户不存在: 404");

        InOrder order = inOrder(roleMapper, userMapper);
        order.verify(roleMapper).lockAllRoleIdsShared();
        order.verify(userMapper).selectByIdForUpdate(404L);
        verify(roleMapper, never()).lockAllRoleIds();
        verifyNoInteractions(userRoleMapper, authorizationStateService);
    }

    @Test
    void invalidInputDoesNotAcquireDatabaseLocks() {
        assertThatThrownBy(() -> service.assignRoles(null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户ID不能为空");

        verifyNoInteractions(roleMapper, userMapper, userRoleMapper, authorizationStateService);
    }

    @Test
    void deleteUsesTheSameGraphThenUserLockOrder() {
        when(userMapper.selectByIdForUpdate(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteUser(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户不存在: 404");

        InOrder order = inOrder(roleMapper, userMapper);
        order.verify(roleMapper).lockAllRoleIdsShared();
        order.verify(userMapper).selectByIdForUpdate(404L);
        verify(roleMapper, never()).lockAllRoleIds();
        verifyNoInteractions(userRoleMapper, authorizationStateService);
    }

    @Test
    void reversedBatchInputLocksAndDeletesUsersInAscendingOrder() {
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(activeUser(1L));
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(activeUser(2L));
        when(userMapper.deleteById(1L)).thenReturn(1);
        when(userMapper.deleteById(2L)).thenReturn(1);

        service.deleteUsers(List.of(2L, 1L, 2L));

        InOrder order = inOrder(roleMapper, userMapper, userRoleMapper, authorizationStateService);
        order.verify(roleMapper).lockAllRoleIdsShared();
        order.verify(userMapper).selectByIdForUpdate(1L);
        order.verify(userMapper).selectByIdForUpdate(2L);
        order.verify(userRoleMapper).deleteByUserId(1L);
        order.verify(userMapper).deleteById(1L);
        order.verify(userRoleMapper).deleteByUserId(2L);
        order.verify(userMapper).deleteById(2L);
        order.verify(authorizationStateService).invalidateUsers(List.of(1L, 2L));
        verify(roleMapper, never()).lockAllRoleIds();
    }

    private SysUserEntity activeUser(long id) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setDelFlag((byte) 0);
        user.setStatus((byte) 1);
        return user;
    }
}
