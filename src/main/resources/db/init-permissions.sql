-- PermaCore IAM 初始化脚本
-- 可重复执行：补齐默认部门、角色、权限、RBAC3 继承关系、SoD 约束，并将 admin 绑定为超级管理员。

USE permacore_iam;

START TRANSACTION;

-- ============================================================
-- 1. 默认部门
-- ============================================================
INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time)
SELECT 0, '总公司', '/总公司', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = 0 AND dept_name = '总公司');

SET @dept_root_id = (SELECT id FROM sys_dept WHERE parent_id = 0 AND dept_name = '总公司' ORDER BY id LIMIT 1);

INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time)
SELECT @dept_root_id, '技术部', CONCAT('/总公司/', '技术部'), 1, 1, NOW()
FROM DUAL WHERE @dept_root_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = @dept_root_id AND dept_name = '技术部');

INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time)
SELECT @dept_root_id, '人力资源部', CONCAT('/总公司/', '人力资源部'), 2, 1, NOW()
FROM DUAL WHERE @dept_root_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = @dept_root_id AND dept_name = '人力资源部');

INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time)
SELECT @dept_root_id, '财务部', CONCAT('/总公司/', '财务部'), 3, 1, NOW()
FROM DUAL WHERE @dept_root_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = @dept_root_id AND dept_name = '财务部');

INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time)
SELECT @dept_root_id, '市场部', CONCAT('/总公司/', '市场部'), 4, 1, NOW()
FROM DUAL WHERE @dept_root_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = @dept_root_id AND dept_name = '市场部');

SET @dept_tech_id = (SELECT id FROM sys_dept WHERE parent_id = @dept_root_id AND dept_name = '技术部' ORDER BY id LIMIT 1);

INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time)
SELECT @dept_tech_id, '后端开发组', CONCAT('/总公司/技术部/', '后端开发组'), 1, 1, NOW()
FROM DUAL WHERE @dept_tech_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = @dept_tech_id AND dept_name = '后端开发组');

INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time)
SELECT @dept_tech_id, '前端开发组', CONCAT('/总公司/技术部/', '前端开发组'), 2, 1, NOW()
FROM DUAL WHERE @dept_tech_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = @dept_tech_id AND dept_name = '前端开发组');

INSERT INTO sys_dept (parent_id, dept_name, dept_path, sort_order, status, create_time)
SELECT @dept_tech_id, '测试组', CONCAT('/总公司/技术部/', '测试组'), 3, 1, NOW()
FROM DUAL WHERE @dept_tech_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_dept WHERE parent_id = @dept_tech_id AND dept_name = '测试组');

-- ============================================================
-- 2. 默认管理员和角色
-- ============================================================
INSERT INTO sys_user (username, password, nickname, status, create_time)
SELECT 'admin', '$2a$10$lNXvCutfulLhh7VjGB1cou98Omd/UpEVMtRaX5cMZzNpaYBcg8q4W', '超级管理员', 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_ADMIN', '超级管理员', 1, 0, 1, '拥有所有权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_ADMIN');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_MANAGER', '部门经理', 2, 5, 1, '部门级管理权限，继承普通用户权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_MANAGER');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_HR', '人力资源', 2, 6, 1, '人事管理权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_HR');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_AUDITOR', '审计员', 2, 7, 1, '只读审计权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_AUDITOR');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_DEVELOPER', '开发人员', 2, 8, 1, '开发相关权限，继承普通用户权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_DEVELOPER');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_FINANCE', '财务人员', 2, 9, 1, '财务相关权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_FINANCE');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_USER', '普通用户', 2, 10, 1, '基础查询权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_USER');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_GUEST', '访客', 2, 20, 1, '最小只读权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_GUEST');

SET @role_admin_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_ADMIN' LIMIT 1);
SET @role_manager_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_MANAGER' LIMIT 1);
SET @role_hr_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_HR' LIMIT 1);
SET @role_auditor_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_AUDITOR' LIMIT 1);
SET @role_developer_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_DEVELOPER' LIMIT 1);
SET @role_finance_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_FINANCE' LIMIT 1);
SET @role_user_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_USER' LIMIT 1);
SET @role_guest_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_GUEST' LIMIT 1);

-- ============================================================
-- 3. 权限：resource_type 统一为 1=菜单, 2=按钮, 3=API
-- ============================================================
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:user', '用户管理', 1, 0, 0, 1, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:user');
SET @perm_user_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:user' LIMIT 1);

INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:role', '角色管理', 1, 0, 0, 2, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:role');
SET @perm_role_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:role' LIMIT 1);

INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:permission', '权限管理', 1, 0, 0, 3, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:permission');
SET @perm_permission_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:permission' LIMIT 1);

INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:sod', '职责分离', 1, 0, 0, 4, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:sod');
SET @perm_sod_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:sod' LIMIT 1);

INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:dept', '部门管理', 1, 0, 0, 5, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:dept');
SET @perm_dept_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:dept' LIMIT 1);

INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:log', '日志管理', 1, 0, 0, 6, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:log');
SET @perm_log_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:log' LIMIT 1);

INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'admin:*', '超级权限', 2, 0, 0, 99, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'admin:*');

-- 用户管理
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:user:query', '用户查询', 2, 0, @perm_user_menu, 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:user:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:add', '用户新增', 2, 0, @perm_user_menu, 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:edit', '用户编辑', 2, 0, @perm_user_menu, 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:delete', '用户删除', 2, 0, @perm_user_menu, 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:delete');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:assignRole', '分配角色', 2, 0, @perm_user_menu, 5, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:assignRole');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:resetPassword', '重置密码', 2, 0, @perm_user_menu, 6, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:resetPassword');

-- 角色管理
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:role:query', '角色查询', 2, 0, @perm_role_menu, 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:role:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:add', '角色新增', 2, 0, @perm_role_menu, 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:edit', '角色编辑', 2, 0, @perm_role_menu, 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:delete', '角色删除', 2, 0, @perm_role_menu, 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:delete');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:assignPermission', '分配权限', 2, 0, @perm_role_menu, 5, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:assignPermission');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:setInheritance', '设置继承', 2, 0, @perm_role_menu, 6, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:setInheritance');

-- 权限管理
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:permission:query', '权限查询', 2, 0, @perm_permission_menu, 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:permission:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:add', '权限新增', 2, 0, @perm_permission_menu, 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:edit', '权限编辑', 2, 0, @perm_permission_menu, 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:delete', '权限删除', 2, 0, @perm_permission_menu, 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:delete');

-- SoD 职责分离
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:sod:query', 'SoD查询', 2, 0, @perm_sod_menu, 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:sod:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'sod:add', 'SoD新增', 2, 0, @perm_sod_menu, 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'sod:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'sod:edit', 'SoD编辑', 2, 0, @perm_sod_menu, 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'sod:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'sod:delete', 'SoD删除', 2, 0, @perm_sod_menu, 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'sod:delete');

-- 部门和日志
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:dept:query', '部门查询', 2, 0, @perm_dept_menu, 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:dept:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:add', '部门新增', 2, 0, @perm_dept_menu, 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:edit', '部门编辑', 2, 0, @perm_dept_menu, 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:delete', '部门删除', 2, 0, @perm_dept_menu, 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:delete');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:log:query', '日志查询', 2, 0, @perm_log_menu, 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:log:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'log:delete', '日志删除', 2, 0, @perm_log_menu, 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'log:delete');

-- 修正旧库中可能存在的 resource_type / parent_id 失配
UPDATE sys_permission SET resource_type = 1 WHERE perm_key IN ('system:user', 'system:role', 'system:permission', 'system:sod', 'system:dept', 'system:log');
UPDATE sys_permission SET resource_type = 2 WHERE perm_key NOT IN ('system:user', 'system:role', 'system:permission', 'system:sod', 'system:dept', 'system:log') AND perm_key <> 'admin:*';

-- ============================================================
-- 4. 角色权限、继承与 SoD
-- ============================================================
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_admin_id, p.id, NOW()
FROM sys_permission p
WHERE p.status = 1
AND NOT EXISTS (
  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = @role_admin_id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_user_id, p.id, NOW()
FROM sys_permission p
WHERE p.perm_key LIKE '%:query'
AND NOT EXISTS (
  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = @role_user_id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_guest_id, p.id, NOW()
FROM sys_permission p
WHERE p.perm_key IN ('system:user:query', 'system:role:query', 'system:permission:query')
AND NOT EXISTS (
  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = @role_guest_id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_auditor_id, p.id, NOW()
FROM sys_permission p
WHERE (p.perm_key LIKE '%:query' OR p.perm_key LIKE 'system:log%')
AND NOT EXISTS (
  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = @role_auditor_id AND rp.permission_id = p.id
);

INSERT INTO sys_role_inheritance (ancestor_id, descendant_id, depth)
SELECT @role_user_id, @role_manager_id, 1
FROM DUAL WHERE @role_user_id IS NOT NULL AND @role_manager_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_role_inheritance WHERE ancestor_id = @role_user_id AND descendant_id = @role_manager_id);

INSERT INTO sys_role_inheritance (ancestor_id, descendant_id, depth)
SELECT @role_user_id, @role_developer_id, 1
FROM DUAL WHERE @role_user_id IS NOT NULL AND @role_developer_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_role_inheritance WHERE ancestor_id = @role_user_id AND descendant_id = @role_developer_id);

INSERT INTO sys_sod_constraint (constraint_name, role_set, constraint_type, create_time)
SELECT '审计员与开发人员互斥', CONCAT('[', @role_auditor_id, ',', @role_developer_id, ']'), 1, NOW()
FROM DUAL WHERE @role_auditor_id IS NOT NULL AND @role_developer_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_sod_constraint WHERE constraint_name = '审计员与开发人员互斥');

INSERT INTO sys_sod_constraint (constraint_name, role_set, constraint_type, create_time)
SELECT '财务与审计互斥', CONCAT('[', @role_finance_id, ',', @role_auditor_id, ']'), 1, NOW()
FROM DUAL WHERE @role_finance_id IS NOT NULL AND @role_auditor_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_sod_constraint WHERE constraint_name = '财务与审计互斥');

INSERT INTO sys_sod_constraint (constraint_name, role_set, constraint_type, create_time)
SELECT '经理与HR动态互斥', CONCAT('[', @role_manager_id, ',', @role_hr_id, ']'), 2, NOW()
FROM DUAL WHERE @role_manager_id IS NOT NULL AND @role_hr_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_sod_constraint WHERE constraint_name = '经理与HR动态互斥');

SET @admin_user_id = (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1);

INSERT INTO sys_user_role (user_id, role_id, create_time)
SELECT @admin_user_id, @role_admin_id, NOW()
FROM DUAL
WHERE @admin_user_id IS NOT NULL
  AND @role_admin_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = @admin_user_id AND ur.role_id = @role_admin_id
  );

COMMIT;

-- 验证参考：
-- SELECT role_key, role_name FROM sys_role ORDER BY sort_order, id;
-- SELECT perm_key, perm_name, resource_type FROM sys_permission ORDER BY parent_id, sort_order;
-- SELECT * FROM sys_role_inheritance;
-- SELECT * FROM sys_sod_constraint;
