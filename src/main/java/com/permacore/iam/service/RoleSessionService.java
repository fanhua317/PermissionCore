package com.permacore.iam.service;

import com.permacore.iam.domain.vo.SessionRoleStateVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RoleSessionService {
    SessionRoleStateVO buildDefaultState(Long userId);

    SessionRoleStateVO buildState(Long userId, Collection<Long> activeRoleIds);

    Set<Long> resolveEffectiveRoleIds(Collection<Long> directRoleIds);

    Set<String> getPermissionsByEffectiveRoleIds(Set<Long> effectiveRoleIds);

    List<Long> parseRoleIdsClaim(Object value);

    Map<String, Object> buildJwtClaims(Long userId, String username, String nickname, SessionRoleStateVO state);

    void appendSessionState(Map<String, Object> response, SessionRoleStateVO state);
}
