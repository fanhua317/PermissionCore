package com.permacore.iam.service;

import java.util.Collection;

/** Coordinates permission-cache and session invalidation after authorization changes. */
public interface AuthorizationStateService {
    void invalidateAllUsers();

    void invalidateUsers(Collection<Long> userIds);
}
