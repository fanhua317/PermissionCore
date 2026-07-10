package com.permacore.iam.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
import com.permacore.iam.mapper.SysSodConstraintMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.service.AuthorizationStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysRoleServiceImplLockingTest {

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private SysRoleInheritanceMapper roleInheritanceMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysSodConstraintMapper sodConstraintMapper;
    @Mock
    private AuthorizationStateService authorizationStateService;

    private SysRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysRoleServiceImpl(
                roleMapper,
                rolePermissionMapper,
                permissionMapper,
                roleInheritanceMapper,
                userRoleMapper,
                sodConstraintMapper,
                new ObjectMapper(),
                authorizationStateService
        );
        ReflectionTestUtils.setField(service, "baseMapper", roleMapper);
    }

    @Test
    void roleCreationAcquiresExclusiveGraphLockBeforeInsert() {
        SysRoleEntity role = new SysRoleEntity();
        role.setRoleKey("ROLE_NEW");
        role.setRoleName("New Role");
        role.setStatus((byte) 1);
        when(roleMapper.insert(any(SysRoleEntity.class))).thenReturn(1);

        assertThat(service.saveRole(role)).isTrue();

        InOrder order = inOrder(roleMapper);
        order.verify(roleMapper).lockAllRoleIds();
        order.verify(roleMapper).insert(role);
        assertThat(role.getDelFlag()).isZero();
        verifyNoInteractions(authorizationStateService);
    }
}
