package com.permacore.iam.controller;

import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.UserQueryVO;
import com.permacore.iam.mapper.SysDeptMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.service.SysDeptService;
import com.permacore.iam.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SysDeptService deptService;
    @Mock
    private AuthorizationStateService authorizationStateService;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysDeptMapper deptMapper;
    @Mock
    private SysUserMapper userMapper;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(
                userService,
                passwordEncoder,
                deptService,
                authorizationStateService,
                roleMapper,
                deptMapper,
                userMapper);
    }

    @Test
    void deepPageUsesLongOffsetAndReturnsStableMapperRows() {
        UserQueryVO query = new UserQueryVO();
        query.setPageNo(1000);
        query.setPageSize(100);

        SysUserEntity user = new SysUserEntity();
        user.setId(101L);
        user.setUsername("perf_user_101");
        user.setCreateTime(LocalDateTime.of(2026, 7, 10, 12, 0));

        when(userMapper.countUserPage(null, null, false, null, null)).thenReturn(100_000L);
        when(userMapper.selectUserPage(99_900L, 100, null, null, false, null, null))
                .thenReturn(List.of(user));

        Result<PageVO<SysUserEntity>> result = controller.page(query);

        assertThat(result.getData().getTotal()).isEqualTo(100_000L);
        assertThat(result.getData().getRecords()).containsExactly(user);
        verify(userMapper).selectUserPage(99_900L, 100, null, null, false, null, null);
    }

    @Test
    void equalSearchTermsPreserveUsernameOrNicknameContainsSemantics() {
        UserQueryVO query = new UserQueryVO();
        query.setUsername("needle");
        query.setNickname("needle");
        query.setStatus(1);
        query.setDeptId(7L);

        when(userMapper.countUserPage("needle", "needle", true, 1, 7L)).thenReturn(0L);

        Result<PageVO<SysUserEntity>> result = controller.page(query);

        assertThat(result.getData().getTotal()).isZero();
        assertThat(result.getData().getRecords()).isEmpty();
        verify(userMapper).countUserPage("needle", "needle", true, 1, 7L);
        verify(userMapper, never()).selectUserPage(
                0L, 10, "needle", "needle", true, 1, 7L);
    }

    @Test
    void blankFiltersAreIgnoredAndPageBoundsAreClamped() {
        UserQueryVO query = new UserQueryVO();
        query.setPageNo(0);
        query.setPageSize(1000);
        query.setUsername("   ");
        query.setNickname("");

        when(userMapper.countUserPage(null, null, false, null, null)).thenReturn(1L);
        when(userMapper.selectUserPage(0L, 100, null, null, false, null, null))
                .thenReturn(List.of());

        controller.page(query);

        verify(userMapper).selectUserPage(0L, 100, null, null, false, null, null);
    }
}
