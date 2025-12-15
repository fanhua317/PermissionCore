package com.permacore.iam.security;

import com.permacore.iam.domain.entity.SysUserEntity;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 自定义 SecurityUser，用于在 UserDetails 中同时携带 userId 和 username
 */
@Getter
public class SecurityUser extends User {

    private final Long userId;
    private final String nickname;

    public SecurityUser(SysUserEntity user, Collection<? extends GrantedAuthority> authorities) {
        super(user.getUsername(), user.getPassword(),
              user.getStatus() == 1, true, true, true, authorities);
        this.userId = user.getId();
        this.nickname = user.getNickname();
    }
}

