package com.permacore.iam.config;

import com.permacore.iam.service.AuthorizationStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 显式的一次性管理员引导。默认不启用，也不会在后续启动时重置现有密码或覆盖 RBAC 数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationStateService authorizationStateService;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.length() < 8 || adminPassword.length() > 72) {
            throw new IllegalStateException("启用管理员引导时，APP_BOOTSTRAP_ADMIN_PASSWORD 必须为 8-72 位");
        }

        Long adminRoleId = querySingleId("SELECT id FROM sys_role WHERE role_key = 'ROLE_ADMIN' AND del_flag = 0");
        if (adminRoleId == null) {
            throw new IllegalStateException("未找到 ROLE_ADMIN，请先执行 schema.sql 和 init-permissions.sql");
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, del_flag FROM sys_user WHERE username = ? LIMIT 1", "admin");
        Long adminUserId;
        if (users.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO sys_user (username, password, nickname, status, del_flag, create_time)
                    VALUES (?, ?, ?, 1, 0, NOW())
                    """, "admin", passwordEncoder.encode(adminPassword), "超级管理员");
            adminUserId = querySingleId("SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1");
            log.warn("已创建初始管理员；请登录后立即修改密码，并关闭 APP_BOOTSTRAP_ENABLED");
        } else {
            adminUserId = ((Number) users.get(0).get("id")).longValue();
            Number delFlag = (Number) users.get(0).get("del_flag");
            if (delFlag != null && delFlag.intValue() != 0) {
                jdbcTemplate.update(
                        "UPDATE sys_user SET password = ?, status = 1, del_flag = 0, update_time = NOW() WHERE id = ?",
                        passwordEncoder.encode(adminPassword), adminUserId);
                log.warn("已恢复被删除的初始管理员；请登录后立即修改密码，并关闭 APP_BOOTSTRAP_ENABLED");
            } else {
                log.info("管理员已存在，bootstrap 不会重置其密码");
            }
        }

        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id, create_time)
                SELECT ?, ?, NOW()
                WHERE NOT EXISTS (
                    SELECT 1 FROM sys_user_role WHERE user_id = ? AND role_id = ?
                )
                """, adminUserId, adminRoleId, adminUserId, adminRoleId);
        authorizationStateService.invalidateUsers(List.of(adminUserId));
    }

    private Long querySingleId(String sql) {
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1));
        return ids.isEmpty() ? null : ids.get(0);
    }
}
