-- PermaCore IAM 初始化脚本
-- 可重复执行：补齐默认部门、角色、权限、RBAC3 继承关系与 SoD 约束；不创建或绑定管理员账号。

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
-- 2. 默认角色（管理员账号由显式 bootstrap 创建）
-- ============================================================
INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_ADMIN', '超级管理员', 1, 0, 1, '拥有所有权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_ADMIN');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_MANAGER', '部门经理', 1, 5, 1, '部门级管理权限，继承普通用户权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_MANAGER');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_HR', '人力资源', 1, 6, 1, '人事管理权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_HR');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_AUDITOR', '审计员', 1, 7, 1, '只读审计权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_AUDITOR');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_DEVELOPER', '开发人员', 1, 8, 1, '开发相关权限，继承普通用户权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_DEVELOPER');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_FINANCE', '财务人员', 1, 9, 1, '财务相关权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_FINANCE');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_USER', '普通用户', 1, 10, 1, '基础查询权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_USER');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_GUEST', '访客', 1, 20, 1, '最小只读权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_GUEST');

UPDATE sys_role
SET role_type = 1, del_flag = 0
WHERE role_key IN (
  'ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_HR', 'ROLE_AUDITOR',
  'ROLE_DEVELOPER', 'ROLE_FINANCE', 'ROLE_USER', 'ROLE_GUEST'
);

SET @role_admin_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_ADMIN' AND del_flag = 0 LIMIT 1);
SET @role_manager_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_MANAGER' AND del_flag = 0 LIMIT 1);
SET @role_hr_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_HR' AND del_flag = 0 LIMIT 1);
SET @role_auditor_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_AUDITOR' AND del_flag = 0 LIMIT 1);
SET @role_developer_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_DEVELOPER' AND del_flag = 0 LIMIT 1);
SET @role_finance_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_FINANCE' AND del_flag = 0 LIMIT 1);
SET @role_user_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_USER' AND del_flag = 0 LIMIT 1);
SET @role_guest_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_GUEST' AND del_flag = 0 LIMIT 1);

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

-- 仅修正本脚本管理的内置权限，绝不改写用户自建 API/按钮权限
UPDATE sys_permission SET resource_type = 1 WHERE perm_key IN ('system:user', 'system:role', 'system:permission', 'system:sod', 'system:dept', 'system:log');
UPDATE sys_permission SET resource_type = 2 WHERE perm_key IN (
  'system:user:query', 'user:add', 'user:edit', 'user:delete', 'user:assignRole', 'user:resetPassword',
  'system:role:query', 'role:add', 'role:edit', 'role:delete', 'role:assignPermission', 'role:setInheritance',
  'system:permission:query', 'permission:add', 'permission:edit', 'permission:delete',
  'system:sod:query', 'sod:add', 'sod:edit', 'sod:delete',
  'system:dept:query', 'dept:add', 'dept:edit', 'dept:delete',
  'system:log:query', 'log:delete'
);

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

-- 普通用户和访客只使用无需管理权限的本人会话接口，不默认获得后台目录、组织或日志读取能力。
-- 只撤销旧初始化脚本曾自动授予的已知基线项；其他历史/自定义授权仍交由权限负责人审阅。
DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_permission p ON p.id = rp.permission_id
WHERE rp.role_id IN (@role_user_id, @role_guest_id)
  AND p.perm_key IN (
    'system:user:query', 'system:role:query', 'system:permission:query',
    'system:sod:query', 'system:dept:query', 'system:log:query'
  );

INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_auditor_id, p.id, NOW()
FROM sys_permission p
WHERE p.perm_key IN (
  'system:user:query', 'system:role:query', 'system:permission:query',
  'system:sod:query', 'system:dept:query', 'system:log', 'system:log:query'
)
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

-- 任何既有用户若违反 SSD，使用 CHECK 守卫让脚本失败并回滚，禁止把脏分配静默带入新基线。
CREATE TEMPORARY TABLE permacore_ssd_guard (
  ok TINYINT NOT NULL,
  CONSTRAINT chk_permacore_ssd_guard CHECK (ok = 1)
) ENGINE=InnoDB;

INSERT INTO permacore_ssd_guard (ok)
WITH RECURSIVE role_closure AS (
  SELECT ur.user_id, ur.role_id
  FROM sys_user_role ur
  INNER JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = 0
  UNION DISTINCT
  SELECT rc.user_id, ri.ancestor_id
  FROM role_closure rc
  INNER JOIN sys_role_inheritance ri ON ri.descendant_id = rc.role_id
), ssd_violations AS (
  SELECT rc.user_id, sc.id AS constraint_id
  FROM role_closure rc
  INNER JOIN sys_sod_constraint sc ON sc.constraint_type = 1
  INNER JOIN JSON_TABLE(
    sc.role_set,
    '$[*]' COLUMNS (role_id BIGINT PATH '$')
  ) roles_in_constraint ON roles_in_constraint.role_id = rc.role_id
  GROUP BY rc.user_id, sc.id
  HAVING COUNT(DISTINCT rc.role_id) >= 2
)
SELECT CASE WHEN EXISTS (SELECT 1 FROM ssd_violations) THEN 0 ELSE 1 END;

DROP TEMPORARY TABLE permacore_ssd_guard;

-- RBAC 基线可能改变有效权限；O(1) 递增全局版本后，所有旧 access/refresh token
-- 都会在下一次数据库门禁校验时立即失效，不扫描 sys_user。
UPDATE sys_authorization_state
SET global_auth_version = global_auth_version + 1
WHERE id = 1;

COMMIT;

-- 验证参考：
-- SELECT role_key, role_name FROM sys_role ORDER BY sort_order, id;
-- SELECT perm_key, perm_name, resource_type FROM sys_permission ORDER BY parent_id, sort_order;
-- SELECT * FROM sys_role_inheritance;
-- SELECT * FROM sys_sod_constraint;
