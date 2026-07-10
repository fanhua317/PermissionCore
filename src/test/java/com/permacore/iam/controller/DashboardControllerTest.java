package com.permacore.iam.controller;

import com.permacore.iam.domain.entity.SysLoginLogEntity;
import com.permacore.iam.domain.entity.SysOperLogEntity;
import com.permacore.iam.domain.vo.DashboardStatsVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.mapper.DashboardMapper;
import com.permacore.iam.mapper.SysLoginLogMapper;
import com.permacore.iam.mapper.SysOperLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardMapper dashboardMapper;
    @Mock
    private SysLoginLogMapper loginLogMapper;
    @Mock
    private SysOperLogMapper operLogMapper;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(dashboardMapper, loginLogMapper, operLogMapper);
    }

    @Test
    void statsComeFromOneAggregateMapperCallWithUnchangedFieldNames() {
        DashboardStatsVO aggregate = new DashboardStatsVO();
        aggregate.setUserCount(10);
        aggregate.setRoleCount(20);
        aggregate.setPermissionCount(30);
        aggregate.setDeptCount(40);
        aggregate.setTodayLoginCount(50);
        aggregate.setTodayOperCount(60);
        when(dashboardMapper.selectStats(any(LocalDateTime.class))).thenReturn(aggregate);

        Result<Map<String, Object>> result = controller.getStats();

        assertThat(result.getData()).containsExactly(
                Map.entry("userCount", 10L),
                Map.entry("roleCount", 20L),
                Map.entry("permissionCount", 30L),
                Map.entry("deptCount", 40L),
                Map.entry("todayLoginCount", 50L),
                Map.entry("todayOperCount", 60L)
        );
        verify(dashboardMapper).selectStats(any(LocalDateTime.class));
    }

    @Test
    void recentEndpointsUseFixedLimitQueriesWithoutPaginationCounts() {
        SysLoginLogEntity loginLog = new SysLoginLogEntity();
        SysOperLogEntity operLog = new SysOperLogEntity();
        when(loginLogMapper.selectRecent(5)).thenReturn(List.of(loginLog));
        when(operLogMapper.selectRecent(5)).thenReturn(List.of(operLog));

        assertThat(controller.getRecentLogins().getData()).containsExactly(loginLog);
        assertThat(controller.getRecentOperations().getData()).containsExactly(operLog);

        verify(loginLogMapper).selectRecent(5);
        verify(operLogMapper).selectRecent(5);
    }
}
