package com.permacore.iam.controller;

import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.ResultCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/test/admin")
public class DbInitController {

    private final JdbcTemplate jdbcTemplate;

    public DbInitController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/init-db")
    @Transactional
    public Result<String> runInitSql() {
        try {
            ClassPathResource resource = new ClassPathResource("db/init-permissions.sql");
            String sql;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                sql = reader.lines().collect(Collectors.joining("\n"));
            }

            // Split by semicolon followed by newline; allow multiple statements
            String[] statements = sql.split(";\\s*(?=\\n|$)");
            for (String stmt : statements) {
                String s = stmt.trim();
                if (s.isEmpty())
                    continue;
                // Skip comments
                if (s.startsWith("--"))
                    continue;
                jdbcTemplate.execute(s);
            }
            return Result.success("Init SQL executed successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("Init SQL failed: " + e.getMessage());
        }
    }

    @GetMapping("/verify-db")
    public Result<Object> verifyDb() {
        try {
            // fetch ROLE_ADMIN id safely
            java.util.List<java.util.Map<String, Object>> roleRows = jdbcTemplate
                    .queryForList("SELECT id FROM sys_role WHERE role_key = ? LIMIT 1", "ROLE_ADMIN");
            if (roleRows.isEmpty()) {
                java.util.Map<String, Object> resp = new java.util.HashMap<>();
                resp.put("roleExists", false);
                return Result.success(resp);
            }
            Number roleIdNum = (Number) roleRows.get(0).get("id");
            Long roleId = roleIdNum == null ? null : roleIdNum.longValue();

            java.util.List<java.util.Map<String, Object>> perms = jdbcTemplate.queryForList(
                    "SELECT p.perm_key FROM sys_permission p JOIN sys_role_permission rp ON p.id = rp.permission_id WHERE rp.role_id = ?",
                    roleId);
            java.util.List<java.util.Map<String, Object>> userRoles = jdbcTemplate.queryForList(
                    "SELECT * FROM sys_user_role WHERE user_id = (SELECT id FROM sys_user WHERE username = 'admin')");
            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("roleExists", true);
            resp.put("roleId", roleId);
            resp.put("permissions", perms);
            resp.put("userRoles", userRoles);
            return Result.success(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("Verify failed: " + e.getMessage());
        }
    }

    @PostMapping("/ensure-admin-binding")
    @Transactional
    public Result<Object> ensureAdminBinding() {
        try {
            // ensure ROLE_ADMIN exists
            Long roleId = null;
            java.util.List<java.util.Map<String, Object>> roleRows = jdbcTemplate
                    .queryForList("SELECT id FROM sys_role WHERE role_key = ? LIMIT 1", "ROLE_ADMIN");
            if (roleRows.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time) VALUES (?,?,?,?,?,?,NOW())",
                        "ROLE_ADMIN", "超级管理员", 1, 0, 1, "拥有所有权限");
                roleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_key = ? LIMIT 1", Long.class,
                        "ROLE_ADMIN");
            } else {
                roleId = ((Number) roleRows.get(0).get("id")).longValue();
            }

            // ensure permission keys
            String[] permKeys = new String[] { "admin:*", "system:user:query", "user:add", "user:edit", "user:delete",
                    "user:assignRole", "user:resetPassword" };
            for (String key : permKeys) {
                java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate
                        .queryForList("SELECT id FROM sys_permission WHERE perm_key = ? LIMIT 1", key);
                if (rows.isEmpty()) {
                    jdbcTemplate.update(
                            "INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time) VALUES (?,?,?,?,?,?,?,NOW())",
                            key, key, 2, 0, 0, 0, 1);
                }
            }

            // ensure role_permission bindings
            java.util.List<java.util.Map<String, Object>> permRows = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_permission WHERE perm_key IN (?,?,?,?,?,?,?)",
                    (Object) permKeys);
            for (java.util.Map<String, Object> pr : permRows) {
                Number pid = (Number) pr.get("id");
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM sys_role_permission WHERE role_id = ? AND permission_id = ?",
                        Integer.class, roleId, pid.longValue());
                if (count == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO sys_role_permission (role_id, permission_id, create_time) VALUES (?,?,NOW())",
                            roleId, pid.longValue());
                }
            }

            // ensure admin user is bound to role
            Long adminId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ? LIMIT 1", Long.class,
                    "admin");
            Integer exist = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sys_user_role WHERE user_id = ? AND role_id = ?", Integer.class, adminId,
                    roleId);
            if (exist == 0) {
                jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, create_time) VALUES (?,?,NOW())",
                        adminId, roleId);
            }

            // return verification
            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("roleId", roleId);
            resp.put("adminId", adminId);
            resp.put("permissionsCount", permRows.size());
            return Result.success(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("ensure failed: " + e.getMessage());
        }
    }

    @GetMapping("/list-perms")
    public Result<java.util.List<java.util.Map<String, Object>>> listPerms() {
        try {
            java.util.List<java.util.Map<String, Object>> list = jdbcTemplate
                    .queryForList("SELECT id,perm_key,perm_name FROM sys_permission ORDER BY id DESC LIMIT 200");
            return Result.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.<java.util.List<java.util.Map<String, Object>>>error(
                    com.permacore.iam.domain.vo.ResultCode.ERROR);
        }
    }
}
