package com.permacore.iam.service.impl;

import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.service.AuthorizationStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationStateServiceImpl implements AuthorizationStateService {

    private final SysUserMapper userMapper;

    @Override
    public void invalidateAllUsers() {
        int updated = userMapper.incrementGlobalAuthVersion();
        if (updated != 1) {
            throw new IllegalStateException("全局授权版本单例行不存在");
        }
        log.info("全部授权状态已失效: globalAuthVersionIncremented=true");
    }

    @Override
    public void invalidateUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        userIds.stream().filter(Objects::nonNull).forEach(distinctIds::add);
        List<Long> ids = List.copyOf(distinctIds);
        if (ids.isEmpty()) {
            return;
        }
        int updated = 0;
        for (int start = 0; start < ids.size(); start += 500) {
            int end = Math.min(start + 500, ids.size());
            updated += userMapper.incrementAuthVersions(ids.subList(start, end));
        }
        if (updated != ids.size()) {
            log.warn("部分用户授权版本未更新: requested={}, updated={}", ids.size(), updated);
        }
        log.info("授权状态已失效: userCount={}", ids.size());
    }
}
