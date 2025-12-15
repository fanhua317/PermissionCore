package com.permacore.iam.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminPasswordResetRunner implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String username = "admin";
        String newPassword = "Admin@123456";

        String encodedPassword = passwordEncoder.encode(newPassword);

        LambdaUpdateWrapper<SysUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SysUserEntity::getUsername, username)
                     .set(SysUserEntity::getPassword, encodedPassword);

        int rows = userMapper.update(null, updateWrapper);
        if (rows > 0) {
            log.info("Admin password reset to '{}' (hash: {})", newPassword, encodedPassword);
        } else {
            log.warn("Admin user not found, could not reset password.");
        }
    }
}

