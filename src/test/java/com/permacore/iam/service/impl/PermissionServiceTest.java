package com.permacore.iam.service.impl;

import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private SysRoleInheritanceMapper roleInheritanceMapper;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(
                rolePermissionMapper,
                permissionMapper,
                roleInheritanceMapper);
    }

    @Test
    void adminWildcardExpandsToAllEnabledPermissions() {
        when(rolePermissionMapper.selectPermissionIdsByRoleIds(anySet())).thenReturn(Set.of(1L));
        when(permissionMapper.selectPermKeysByIds(anySet())).thenReturn(Set.of("admin:*"));
        when(permissionMapper.selectAllEnabledPermKeys())
                .thenReturn(Set.of("admin:*", "system:user:query", "user:add"));

        Set<String> permissions = service.getPermissionsByRoleIds(Set.of(10L));

        assertThat(permissions).containsExactly("admin:*", "system:user:query", "user:add");
    }
}
