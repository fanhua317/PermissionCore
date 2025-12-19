-- PermaCore 初始化脚本：创建超级管理员角色、完整权限并将 admin 绑定为超级管理员
-- 使用方法：
-- 1) 将本文件上传到数据库服务器或从项目根目录执行：
--    mysql -u<user> -p permacore_iam < src/main/resources/db/init-permissions.sql
-- 2) 执行完成后，重启后端或清除权限缓存以立即生效。

USE permacore_iam;

START TRANSACTION;

-- 1. 创建超级管理员角色（如果不存在）
INSERT INTO sys_role (role_key, role_name, role_type, sort_order, status, remark, create_time)
SELECT 'ROLE_ADMIN', '超级管理员', 1, 0, 1, '拥有所有权限', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'ROLE_ADMIN');

-- 取回超级管理员角色 ID
SET @role_admin_id = (SELECT id FROM sys_role WHERE role_key = 'ROLE_ADMIN' LIMIT 1);

-- 2. 插入完整的权限列表（如果不存在）

-- ============ 用户管理权限 ============
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:user:query', '用户-查询', 2, 0, 0, 1, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:user:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:add', '用户-创建', 2, 0, 0, 2, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:edit', '用户-编辑', 2, 0, 0, 3, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:delete', '用户-删除', 2, 0, 0, 4, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:delete');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:assignRole', '用户-分配角色', 2, 0, 0, 5, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:assignRole');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:resetPassword', '用户-重置密码', 2, 0, 0, 6, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:resetPassword');

-- ============ 角色管理权限 ============
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:role:query', '角色-查询', 2, 0, 0, 11, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:role:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:add', '角色-创建', 2, 0, 0, 12, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:edit', '角色-编辑', 2, 0, 0, 13, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:delete', '角色-删除', 2, 0, 0, 14, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:delete');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:assignPermission', '角色-分配权限', 2, 0, 0, 15, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:assignPermission');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'role:setInheritance', '角色-设置继承', 2, 0, 0, 16, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'role:setInheritance');

-- ============ 权限管理权限 ============
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:permission:query', '权限-查询', 2, 0, 0, 21, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:permission:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:add', '权限-创建', 2, 0, 0, 22, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:edit', '权限-编辑', 2, 0, 0, 23, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'permission:delete', '权限-删除', 2, 0, 0, 24, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'permission:delete');

-- ============ 部门管理权限 ============
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:dept:query', '部门-查询', 2, 0, 0, 31, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:dept:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:add', '部门-创建', 2, 0, 0, 32, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:edit', '部门-编辑', 2, 0, 0, 33, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'dept:delete', '部门-删除', 2, 0, 0, 34, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dept:delete');

-- ============ 日志管理权限 ============
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:log:query', '日志-查询', 2, 0, 0, 41, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:log:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'log:delete', '日志-删除', 2, 0, 0, 42, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'log:delete');

-- 管理员通配符权限（可选）
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'admin:*', '管理员-全部权限', 2, 0, 0, 99, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'admin:*');

-- 3. 将所有权限绑定到超级管理员角色
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_admin_id, p.id, NOW()
FROM sys_permission p
WHERE p.status = 1
AND NOT EXISTS (
  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = @role_admin_id AND rp.permission_id = p.id
);

-- 4. 把 admin 用户绑定到超级管理员角色（假设 admin 用户存在且 username='admin'）
SET @admin_user_id = (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1);

-- 只有当 admin 用户存在且 role id 不为空时再插入
INSERT INTO sys_user_role (user_id, role_id, create_time)
SELECT @admin_user_id, @role_admin_id, NOW()
FROM DUAL
WHERE @admin_user_id IS NOT NULL
  AND @role_admin_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = @admin_user_id AND ur.role_id = @role_admin_id
  );

COMMIT;

-- 验证查询（手动执行）
-- SELECT * FROM sys_role WHERE role_key = 'ROLE_ADMIN';
-- SELECT p.perm_key, p.perm_name FROM sys_permission p JOIN sys_role_permission rp ON p.id = rp.permission_id WHERE rp.role_id = @role_admin_id ORDER BY p.sort_order;
-- SELECT r.role_name FROM sys_user u JOIN sys_user_role ur ON u.id = ur.user_id JOIN sys_role r ON ur.role_id = r.id WHERE u.username = 'admin';

-- 注意：若项目在运行时缓存了权限（Redis/Caffeine），请清除缓存或重启应用以使权限生效。


