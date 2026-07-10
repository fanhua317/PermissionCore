package com.permacore.iam.mapper;

import com.permacore.iam.domain.vo.DashboardStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 仪表盘只读聚合查询。
 */
@Mapper
public interface DashboardMapper {

    DashboardStatsVO selectStats(@Param("todayStart") LocalDateTime todayStart);
}
