package com.permacore.iam.service;

import java.util.Collection;

/** Coordinates durable personal and global authorization-version invalidation. */
public interface AuthorizationStateService {
    /** Invalidates every issued token with a single global version increment. */
    void invalidateAllUsers();

    /** Invalidates tokens for the specified users by incrementing personal versions. */
    void invalidateUsers(Collection<Long> userIds);
}
