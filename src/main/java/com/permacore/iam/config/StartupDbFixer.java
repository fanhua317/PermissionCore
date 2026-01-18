package com.permacore.iam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 应用启动时自动修复 RBAC 表，确保 ROLE_ADMIN、权限和 admin 用户绑定存在。
 */
@Component
@Order
public class StartupDbFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDbFixer.class);

    private final JdbcTemplate jdbcTemplate;

    public StartupDbFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try {
            log.info("StartupDbFixer: 开始检查并修复管理员角色/权限绑定...");

            // ensure ROLE_ADMIN exists
            Long roleId = null;
            List<Map<String, Object>> roleRows = jdbcTemplate
                    .queryForList("SELECT id FROM sys_role WHERE role_key = ? LIMIT 1", "ROLE_ADMIN");
            if (roleRows.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time) VALUES (?,?,?,?,?,?,NOW())",
                        "ROLE_ADMIN", "超级管理员", 1, 0, 1, "拥有所有权限");
                roleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_key = ? LIMIT 1", Long.class,
                        "ROLE_ADMIN");
                log.info("StartupDbFixer: 已插入 ROLE_ADMIN (id={})", roleId);
            } else {
                roleId = ((Number) roleRows.get(0).get("id")).longValue();
                log.info("StartupDbFixer: ROLE_ADMIN 已存在 (id={})", roleId);
            }

            // ensure permission keys
            String[] permKeys = new String[] { "admin:*", "system:user:query", "user:add", "user:edit", "user:delete",
                    "user:assignRole", "user:resetPassword" };
            for (String key : permKeys) {
                List<Map<String, Object>> rows = jdbcTemplate
                        .queryForList("SELECT id FROM sys_permission WHERE perm_key = ? LIMIT 1", key);
                if (rows.isEmpty()) {
                    jdbcTemplate.update(
                            "INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time) VALUES (?,?,?,?,?,?,?,NOW())",
                            key, key, 2, 0, 0, 0, 1);
                    log.info("StartupDbFixer: 已插入权限 {}", key);
                }
            }

            // ensure role_permission bindings
            String placeholders = String.join(",", java.util.Collections.nCopies(permKeys.length, "?"));
            List<Map<String, Object>> permRows = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_permission WHERE perm_key IN (" + placeholders + ")",
                    (Object[]) permKeys);
            for (Map<String, Object> pr : permRows) {
                Number pid = (Number) pr.get("id");
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM sys_role_permission WHERE role_id = ? AND permission_id = ?",
                        Integer.class, roleId, pid.longValue());
                if (count == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO sys_role_permission (role_id, permission_id, create_time) VALUES (?,?,NOW())",
                            roleId, pid.longValue());
                    log.info("StartupDbFixer: 绑定 role_id={} permission_id={}", roleId, pid.longValue());
                }
            }

            // ensure admin user is bound to role
            List<Map<String, Object>> adminRows = jdbcTemplate
                    .queryForList("SELECT id FROM sys_user WHERE username = ? LIMIT 1", "admin");
            if (adminRows.isEmpty()) {
                log.warn("StartupDbFixer: 未找到 admin 用户，跳过绑定步骤。请先创建 admin 用户。");
            } else {
                Long adminId = ((Number) adminRows.get(0).get("id")).longValue();
                Integer exist = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM sys_user_role WHERE user_id = ? AND role_id = ?", Integer.class, adminId,
                        roleId);
                if (exist == 0) {
                    jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, create_time) VALUES (?,?,NOW())",
                            adminId, roleId);
                    log.info("StartupDbFixer: 已将 admin(userId={}) 绑定到 ROLE_ADMIN(roleId={})", adminId, roleId);
                } else {
                    log.info("StartupDbFixer: admin 已经绑定到 ROLE_ADMIN");
                }
            }

            log.info("StartupDbFixer: 修复完成");
        } catch (Exception e) {
            log.error("StartupDbFixer: 修复过程中发生异常", e);
            throw e;
        }
    }
}
