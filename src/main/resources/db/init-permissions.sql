-- PermaCore 初始化脚本：创建超级管理员角色、完整权限并将 admin 绑定为超级管理员
-- 支持 RBAC3 模型：包含角色继承和职责分离（SoD）约束
-- 使用方法：
-- 1) 将本文件上传到数据库服务器或从项目根目录执行：
--    mysql -u<user> -p permacore_iam < src/main/resources/db/init-permissions.sql
-- 2) 执行完成后，重启后端或清除权限缓存以立即生效。

USE permacore_iam;

START TRANSACTION;

-- ============================================================
-- 1. 创建角色（如果不存在）
-- ============================================================
INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_ADMIN', '超级管理员', 1, 0, 1, '拥有所有权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_ADMIN');

INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_USER', '普通用户', 2, 10, 1, '基础权限', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_USER');

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

-- 取回角色 ID
SET @role_admin_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_ADMIN' LIMIT 1);
SET @role_user_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_USER' LIMIT 1);
SET @role_manager_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_MANAGER' LIMIT 1);
SET @role_hr_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_HR' LIMIT 1);
SET @role_auditor_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_AUDITOR' LIMIT 1);
SET @role_developer_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_DEVELOPER' LIMIT 1);
SET @role_finance_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_FINANCE' LIMIT 1);

-- ============================================================
-- 2. 插入层级权限结构（菜单 -> 按钮/操作）
-- resource_type: 1=菜单, 2=按钮/操作, 3=API接口
-- ============================================================

-- ==================== 一级菜单权限 ====================
-- 用户管理（一级菜单）
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:user', '用户管理', 1, 0, 0, 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:user');
SET @perm_user_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:user' LIMIT 1);

-- 角色管理（一级菜单）
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:role', '角色管理', 1, 0, 0, 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:role');
SET @perm_role_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:role' LIMIT 1);

-- 权限管理（一级菜单）
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:permission', '权限管理', 1, 0, 0, 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:permission');
SET @perm_permission_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:permission' LIMIT 1);

-- 部门管理（一级菜单）
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:dept', '部门管理', 1, 0, 0, 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:dept');
SET @perm_dept_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:dept' LIMIT 1);

-- 日志管理（一级菜单）
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:log', '日志管理', 1, 0, 0, 5, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:log');
SET @perm_log_menu = (SELECT id FROM sys_permission WHERE perm_key = 'system:log' LIMIT 1);

-- ==================== 用户管理子权限（二级） ====================
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:user:query', '用户查询', 2, 0, IFNULL(@perm_user_menu, 0), 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:user:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:add', '用户新增', 2, 0, IFNULL(@perm_user_menu, 0), 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:edit', '用户编辑', 2, 0, IFNULL(@perm_user_menu, 0), 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:delete', '用户删除', 2, 0, IFNULL(@perm_user_menu, 0), 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:delete');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:assignRole', '分配角色', 2, 0, IFNULL(@perm_user_menu, 0), 5, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:assignRole');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:resetPassword', '重置密码', 2, 0, IFNULL(@perm_user_menu, 0), 6, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:resetPassword');

-- ==================== 角色管理子权限（二级） ====================
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:role:query', '角色查询', 2, 0, IFNULL(@perm_role_menu, 0), 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:role:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:add', '角色新增', 2, 0, IFNULL(@perm_role_menu, 0), 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:edit', '角色编辑', 2, 0, IFNULL(@perm_role_menu, 0), 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:delete', '角色删除', 2, 0, IFNULL(@perm_role_menu, 0), 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:delete');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:assignPermission', '分配权限', 2, 0, IFNULL(@perm_role_menu, 0), 5, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:assignPermission');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:setInheritance', '设置继承', 2, 0, IFNULL(@perm_role_menu, 0), 6, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:setInheritance');

-- ==================== 权限管理子权限（二级） ====================
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:permission:query', '权限查询', 2, 0, IFNULL(@perm_permission_menu, 0), 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:permission:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:add', '权限新增', 2, 0, IFNULL(@perm_permission_menu, 0), 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:edit', '权限编辑', 2, 0, IFNULL(@perm_permission_menu, 0), 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:delete', '权限删除', 2, 0, IFNULL(@perm_permission_menu, 0), 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:delete');

-- ==================== 部门管理子权限（二级） ====================
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:dept:query', '部门查询', 2, 0, IFNULL(@perm_dept_menu, 0), 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:dept:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:add', '部门新增', 2, 0, IFNULL(@perm_dept_menu, 0), 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:edit', '部门编辑', 2, 0, IFNULL(@perm_dept_menu, 0), 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:delete', '部门删除', 2, 0, IFNULL(@perm_dept_menu, 0), 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:delete');

-- ==================== 日志管理子权限（二级） ====================
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:log:query', '日志查询', 2, 0, IFNULL(@perm_log_menu, 0), 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:log:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'log:delete', '日志删除', 2, 0, IFNULL(@perm_log_menu, 0), 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'log:delete');

-- 管理员通配符权限
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'admin:*', '超级权限', 2, 0, 0, 99, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'admin:*');

-- ============================================================
-- 3. 将所有权限绑定到超级管理员角色
-- ============================================================
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_admin_id, p.id, NOW()
FROM sys_permission p
WHERE p.status = 1
AND NOT EXISTS (
  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = @role_admin_id AND rp.permission_id = p.id
);

-- ============================================================
-- 4. 配置角色继承关系（RBAC3 特性）
-- depth=1 表示直接继承关系
-- ============================================================
-- 部门经理 继承 普通用户
INSERT INTO sys_role_inheritance (ancestor_id, descendant_id, depth)
SELECT @role_user_id, @role_manager_id, 1
FROM DUAL WHERE @role_user_id IS NOT NULL AND @role_manager_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_role_inheritance WHERE ancestor_id = @role_user_id AND descendant_id = @role_manager_id);

-- 开发人员 继承 普通用户
INSERT INTO sys_role_inheritance (ancestor_id, descendant_id, depth)
SELECT @role_user_id, @role_developer_id, 1
FROM DUAL WHERE @role_user_id IS NOT NULL AND @role_developer_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_role_inheritance WHERE ancestor_id = @role_user_id AND descendant_id = @role_developer_id);

-- ============================================================
-- 5. 配置 SoD（职责分离）约束（RBAC3 特性）
-- constraint_type: 1=静态互斥(SSD), 2=动态互斥(DSD)
-- ============================================================
-- 静态互斥约束：审计员 与 开发人员 不能同时分配给同一用户
INSERT INTO sys_sod_constraint (constraint_name, role_set, constraint_type, create_time)
SELECT '审计员与开发人员互斥', CONCAT('[', @role_auditor_id, ',', @role_developer_id, ']'), 1, NOW()
FROM DUAL WHERE @role_auditor_id IS NOT NULL AND @role_developer_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_sod_constraint WHERE constraint_name = '审计员与开发人员互斥');

-- 静态互斥约束：财务人员 与 审计员 不能同时分配给同一用户（财务与审计分离）
INSERT INTO sys_sod_constraint (constraint_name, role_set, constraint_type, create_time)
SELECT '财务与审计互斥', CONCAT('[', @role_finance_id, ',', @role_auditor_id, ']'), 1, NOW()
FROM DUAL WHERE @role_finance_id IS NOT NULL AND @role_auditor_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_sod_constraint WHERE constraint_name = '财务与审计互斥');

-- 动态互斥约束：同一会话中不能同时激活 部门经理 和 人力资源 角色
INSERT INTO sys_sod_constraint (constraint_name, role_set, constraint_type, create_time)
SELECT '经理与HR动态互斥', CONCAT('[', @role_manager_id, ',', @role_hr_id, ']'), 2, NOW()
FROM DUAL WHERE @role_manager_id IS NOT NULL AND @role_hr_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_sod_constraint WHERE constraint_name = '经理与HR动态互斥');

-- ============================================================
-- 6. 把 admin 用户绑定到超级管理员角色
-- ============================================================
SET @admin_user_id = (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1);

INSERT INTO sys_user_role (user_id, role_id, create_time)
SELECT @admin_user_id, @role_admin_id, NOW()
FROM DUAL
WHERE @admin_user_id IS NOT NULL
  AND @role_admin_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = @admin_user_id AND ur.role_id = @role_admin_id
  );

-- ============================================================
-- 7. 为普通角色分配权限
-- ============================================================
-- 普通用户：查询权限
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_user_id, id, NOW() FROM sys_permission WHERE perm_key LIKE '%:query' AND @role_user_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = @role_user_id AND permission_id = sys_permission.id);

-- 审计员：所有查询权限 + 日志查询
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_auditor_id, id, NOW() FROM sys_permission WHERE (perm_key LIKE '%:query' OR perm_key LIKE 'system:log%') AND @role_auditor_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = @role_auditor_id AND permission_id = sys_permission.id);

COMMIT;

-- ============================================================
-- 验证查询（手动执行）
-- ============================================================
-- SELECT * FROM sys_role;
-- SELECT p.perm_key, p.perm_name, p.parent_id FROM sys_permission p ORDER BY p.parent_id, p.sort_order;
-- SELECT * FROM sys_role_inheritance;
-- SELECT * FROM sys_sod_constraint;

-- 注意：若项目在运行时缓存了权限（Redis/Caffeine），请清除缓存或重启应用以使权限生效。