package com.permacore.iam.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminPasswordResetRunner implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.admin.default-password:#{null}}")
    private String adminPassword;

    public AdminPasswordResetRunner(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("No admin password reset configured (app.admin.default-password not set), skipping.");
            return;
        }

        String username = "admin";
        String encodedPassword = passwordEncoder.encode(adminPassword);

        LambdaUpdateWrapper<SysUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SysUserEntity::getUsername, username)
                .set(SysUserEntity::getPassword, encodedPassword);

        int rows = userMapper.update(null, updateWrapper);
        if (rows > 0) {
            log.info("Admin password has been reset successfully.");
        } else {
            log.warn("Admin user not found, could not reset password.");
        }
    }
}
