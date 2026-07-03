package com.permacore.iam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order
public class StartupDbFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDbFixer.class);
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123456";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public StartupDbFixer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            log.info("StartupDbFixer: checking RBAC baseline...");
            ensureDefaultDepartments();
            Long adminRoleId = ensureRole("ROLE_ADMIN", "超级管理员", 1, 0, "拥有所有权限");
            ensureRole("ROLE_MANAGER", "部门经理", 2, 5, "部门级管理权限，继承普通用户权限");
            ensureRole("ROLE_HR", "人力资源", 2, 6, "人事管理权限");
            ensureRole("ROLE_AUDITOR", "审计员", 2, 7, "只读审计权限");
            ensureRole("ROLE_DEVELOPER", "开发人员", 2, 8, "开发相关权限，继承普通用户权限");
            ensureRole("ROLE_FINANCE", "财务人员", 2, 9, "财务相关权限");
            ensureRole("ROLE_USER", "普通用户", 2, 10, "基础查询权限");
            ensureRole("ROLE_GUEST", "访客", 2, 20, "最小只读权限");

            Map<String, Long> permissionIds = ensurePermissions();
            bindAdminRoleToAllPermissions(adminRoleId);
            ensureAdminUserAndRole(adminRoleId);
            ensureRoleInheritance();
            ensureSodConstraints();
            log.info("StartupDbFixer: RBAC baseline checked, permissionCount={}", permissionIds.size());
        } catch (Exception e) {
            log.error("StartupDbFixer failed", e);
            throw e;
        }
    }

    private void ensureDefaultDepartments() {
        Long rootId = ensureDept(0L, "总公司", "/总公司", 0);
        Long techId = ensureDept(rootId, "技术部", "/总公司/技术部", 1);
        ensureDept(rootId, "人力资源部", "/总公司/人力资源部", 2);
        ensureDept(rootId, "财务部", "/总公司/财务部", 3);
        ensureDept(rootId, "市场部", "/总公司/市场部", 4);
        ensureDept(techId, "后端开发组", "/总公司/技术部/后端开发组", 1);
        ensureDept(techId, "前端开发组", "/总公司/技术部/前端开发组", 2);
        ensureDept(techId, "测试组", "/总公司/技术部/测试组", 3);
    }

    private Long ensureDept(Long parentId, String deptName, String deptPath, int sortOrder) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM sys_dept WHERE parent_id = ? AND dept_name = ? AND del_flag = 0 LIMIT 1",
                parentId, deptName);
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time) VALUES (?,?,?,?,1,NOW())",
                    parentId, deptName, deptPath, sortOrder);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_dept WHERE parent_id = ? AND dept_name = ? AND del_flag = 0 LIMIT 1",
                    Long.class, parentId, deptName);
        }
        return ((Number) rows.get(0).get("id")).longValue();
    }

    private Long ensureRole(String roleKey, String roleName, int roleType, int sortOrder, String remark) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE role_key = ? LIMIT 1", roleKey);
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time) VALUES (?,?,?,?,1,?,NOW())",
                    roleKey, roleName, roleType, sortOrder, remark);
            return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_key = ? LIMIT 1", Long.class,
                    roleKey);
        }

        Long roleId = ((Number) rows.get(0).get("id")).longValue();
        jdbcTemplate.update(
                "UPDATE sys_role SET role_name = ?, role_type = ?, sort_order = ?, status = 1, remark = ? WHERE id = ?",
                roleName, roleType, sortOrder, remark, roleId);
        return roleId;
    }

    private Map<String, Long> ensurePermissions() {
        List<PermissionSeed> seeds = List.of(
                new PermissionSeed("system:user", "用户管理", 1, null, 1),
                new PermissionSeed("system:role", "角色管理", 1, null, 2),
                new PermissionSeed("system:permission", "权限管理", 1, null, 3),
                new PermissionSeed("system:sod", "职责分离", 1, null, 4),
                new PermissionSeed("system:dept", "部门管理", 1, null, 5),
                new PermissionSeed("system:log", "日志管理", 1, null, 6),
                new PermissionSeed("admin:*", "超级权限", 2, null, 99),
                new PermissionSeed("system:user:query", "用户查询", 2, "system:user", 1),
                new PermissionSeed("user:add", "用户新增", 2, "system:user", 2),
                new PermissionSeed("user:edit", "用户编辑", 2, "system:user", 3),
                new PermissionSeed("user:delete", "用户删除", 2, "system:user", 4),
                new PermissionSeed("user:assignRole", "分配角色", 2, "system:user", 5),
                new PermissionSeed("user:resetPassword", "重置密码", 2, "system:user", 6),
                new PermissionSeed("system:role:query", "角色查询", 2, "system:role", 1),
                new PermissionSeed("role:add", "角色新增", 2, "system:role", 2),
                new PermissionSeed("role:edit", "角色编辑", 2, "system:role", 3),
                new PermissionSeed("role:delete", "角色删除", 2, "system:role", 4),
                new PermissionSeed("role:assignPermission", "分配权限", 2, "system:role", 5),
                new PermissionSeed("role:setInheritance", "设置继承", 2, "system:role", 6),
                new PermissionSeed("system:permission:query", "权限查询", 2, "system:permission", 1),
                new PermissionSeed("permission:add", "权限新增", 2, "system:permission", 2),
                new PermissionSeed("permission:edit", "权限编辑", 2, "system:permission", 3),
                new PermissionSeed("permission:delete", "权限删除", 2, "system:permission", 4),
                new PermissionSeed("system:sod:query", "SoD查询", 2, "system:sod", 1),
                new PermissionSeed("sod:add", "SoD新增", 2, "system:sod", 2),
                new PermissionSeed("sod:edit", "SoD编辑", 2, "system:sod", 3),
                new PermissionSeed("sod:delete", "SoD删除", 2, "system:sod", 4),
                new PermissionSeed("system:dept:query", "部门查询", 2, "system:dept", 1),
                new PermissionSeed("dept:add", "部门新增", 2, "system:dept", 2),
                new PermissionSeed("dept:edit", "部门编辑", 2, "system:dept", 3),
                new PermissionSeed("dept:delete", "部门删除", 2, "system:dept", 4),
                new PermissionSeed("system:log:query", "日志查询", 2, "system:log", 1),
                new PermissionSeed("log:delete", "日志删除", 2, "system:log", 2));

        Map<String, Long> ids = new HashMap<>();
        for (PermissionSeed seed : seeds) {
            Long parentId = seed.parentKey() == null ? 0L : ids.getOrDefault(seed.parentKey(), findPermissionId(seed.parentKey()));
            Long id = ensurePermission(seed, parentId == null ? 0L : parentId);
            ids.put(seed.key(), id);
        }
        return ids;
    }

    private Long ensurePermission(PermissionSeed seed, Long parentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM sys_permission WHERE perm_key = ? LIMIT 1", seed.key());
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time) VALUES (?,?,?,?,?,?,1,NOW())",
                    seed.key(), seed.name(), seed.resourceType(), 0, parentId, seed.sortOrder());
            return findPermissionId(seed.key());
        }

        Long permissionId = ((Number) rows.get(0).get("id")).longValue();
        jdbcTemplate.update(
                "UPDATE sys_permission SET perm_name = ?, resource_type = ?, parent_id = ?, sort_order = ?, status = 1 WHERE id = ?",
                seed.name(), seed.resourceType(), parentId, seed.sortOrder(), permissionId);
        return permissionId;
    }

    private Long findPermissionId(String permKey) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_permission WHERE perm_key = ? LIMIT 1", Long.class,
                permKey);
    }

    private void bindAdminRoleToAllPermissions(Long adminRoleId) {
        jdbcTemplate.update("""
                INSERT INTO sys_role_permission (role_id, permission_id, create_time)
                SELECT ?, p.id, NOW()
                FROM sys_permission p
                WHERE p.status = 1
                AND NOT EXISTS (
                    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = ? AND rp.permission_id = p.id
                )
                """, adminRoleId, adminRoleId);
    }

    private void ensureAdminUserAndRole(Long adminRoleId) {
        List<Map<String, Object>> adminRows = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ? LIMIT 1", "admin");
        Long adminId;
        if (adminRows.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password, nickname, status, create_time) VALUES (?,?,?,?,NOW())",
                    "admin", passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD), "超级管理员", 1);
            adminId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ? LIMIT 1", Long.class,
                    "admin");
            log.info("StartupDbFixer: created missing admin user with default password.");
        } else {
            adminId = ((Number) adminRows.get(0).get("id")).longValue();
        }

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user_role WHERE user_id = ? AND role_id = ?",
                Integer.class, adminId, adminRoleId);
        if (exists == null || exists == 0) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, create_time) VALUES (?,?,NOW())",
                    adminId, adminRoleId);
        }
    }

    private void ensureRoleInheritance() {
        ensureInheritance("ROLE_USER", "ROLE_MANAGER");
        ensureInheritance("ROLE_USER", "ROLE_DEVELOPER");
    }

    private void ensureInheritance(String ancestorKey, String descendantKey) {
        Long ancestorId = findRoleId(ancestorKey);
        Long descendantId = findRoleId(descendantKey);
        if (ancestorId == null || descendantId == null) {
            return;
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_role_inheritance WHERE ancestor_id = ? AND descendant_id = ?",
                Integer.class, ancestorId, descendantId);
        if (exists == null || exists == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_role_inheritance (ancestor_id, descendant_id, depth) VALUES (?,?,1)",
                    ancestorId, descendantId);
        }
    }

    private void ensureSodConstraints() {
        ensureSod("审计员与开发人员互斥", "ROLE_AUDITOR", "ROLE_DEVELOPER", 1);
        ensureSod("财务与审计互斥", "ROLE_FINANCE", "ROLE_AUDITOR", 1);
        ensureSod("经理与HR动态互斥", "ROLE_MANAGER", "ROLE_HR", 2);
    }

    private void ensureSod(String name, String firstRoleKey, String secondRoleKey, int type) {
        Long firstRoleId = findRoleId(firstRoleKey);
        Long secondRoleId = findRoleId(secondRoleKey);
        if (firstRoleId == null || secondRoleId == null) {
            return;
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_sod_constraint WHERE constraint_name = ?",
                Integer.class, name);
        if (exists == null || exists == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_sod_constraint (constraint_name, role_set, constraint_type, create_time) VALUES (?,?,?,NOW())",
                    name, "[" + firstRoleId + "," + secondRoleId + "]", type);
        }
    }

    private Long findRoleId(String roleKey) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE role_key = ? LIMIT 1", roleKey);
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("id")).longValue();
    }

    private record PermissionSeed(String key, String name, int resourceType, String parentKey, int sortOrder) {
    }
}
