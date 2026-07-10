package com.permacore.iam.service.impl;

import com.permacore.iam.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AuthorizationStateServiceImplTest {

    @Test
    void incrementsTheDurableAuthorizationVersionOncePerUser() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.incrementAuthVersions(List.of(7L, 8L))).thenReturn(2);
        AuthorizationStateServiceImpl service = new AuthorizationStateServiceImpl(userMapper);

        service.invalidateUsers(List.of(7L, 7L, 8L));

        verify(userMapper).incrementAuthVersions(List.of(7L, 8L));
        verify(userMapper, never()).incrementGlobalAuthVersion();
    }

    @Test
    void globalInvalidationUpdatesOnlyTheSingletonRow() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.incrementGlobalAuthVersion()).thenReturn(1);
        AuthorizationStateServiceImpl service = new AuthorizationStateServiceImpl(userMapper);

        service.invalidateAllUsers();

        verify(userMapper).incrementGlobalAuthVersion();
        verify(userMapper, never()).incrementAuthVersions(org.mockito.ArgumentMatchers.any());
    }
}
