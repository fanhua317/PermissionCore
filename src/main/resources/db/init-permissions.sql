-- PermaCore 初始化脚本：创建超级管理员角色、权限并将 admin 绑定为超级管理员
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

-- 2. 插入若干常用权限（如果不存在）
-- 请根据代码中实际使用的 perm_key 补充或调整列表
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:add', '用户-创建', 2, 0, 0, 0, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:add');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:edit', '用户-编辑', 2, 0, 0, 0, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:edit');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:delete', '用户-删除', 2, 0, 0, 0, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:delete');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'system:user:query', '用户-查询', 2, 0, 0, 0, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'system:user:query');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:assignRole', '用户-分配角色', 2, 0, 0, 0, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:assignRole');
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'user:resetPassword', '用户-重置密码', 2, 0, 0, 0, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user:resetPassword');

-- 可选：为超级管理员赋予一个通配权限记录（如果项目支持），例如 perm_key = '*' 或 'admin:*'
INSERT INTO sys_permission (perm_key, perm_name, resource_type, resource_id, parent_id, sort_order, status, create_time)
SELECT 'admin:*', '管理员-全部权限', 2, 0, 0, 0, 1, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'admin:*');

-- 取回权限 id 列表（基于上面插入或已存在的）
SET @perm_user_add = (SELECT id FROM sys_permission WHERE perm_key = 'user:add' LIMIT 1);
SET @perm_user_edit = (SELECT id FROM sys_permission WHERE perm_key = 'user:edit' LIMIT 1);
SET @perm_user_delete = (SELECT id FROM sys_permission WHERE perm_key = 'user:delete' LIMIT 1);
SET @perm_user_query = (SELECT id FROM sys_permission WHERE perm_key = 'system:user:query' LIMIT 1);
SET @perm_user_assign = (SELECT id FROM sys_permission WHERE perm_key = 'user:assignRole' LIMIT 1);
SET @perm_user_reset = (SELECT id FROM sys_permission WHERE perm_key = 'user:resetPassword' LIMIT 1);
SET @perm_admin_all = (SELECT id FROM sys_permission WHERE perm_key = 'admin:*' LIMIT 1);

-- 3. 将这些权限绑定到超级管理员角色（若尚未绑定）
-- 使用 INSERT ... SELECT 避免重复插入
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT @role_admin_id, p.id, NOW()
FROM sys_permission p
WHERE p.id IN (
  COALESCE(@perm_admin_all, 0),
  COALESCE(@perm_user_add, 0),
  COALESCE(@perm_user_edit, 0),
  COALESCE(@perm_user_delete, 0),
  COALESCE(@perm_user_query, 0),
  COALESCE(@perm_user_assign, 0),
  COALESCE(@perm_user_reset, 0)
)
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
-- SELECT p.perm_key FROM sys_permission p JOIN sys_role_permission rp ON p.id = rp.permission_id WHERE rp.role_id = @role_admin_id;
-- SELECT * FROM sys_user_role WHERE user_id = (SELECT id FROM sys_user WHERE username = 'admin');

-- 注意：若项目在运行时缓存了权限（Redis/Caffeine），请清除缓存或重启应用以使权限生效。

