-- Existing databases can run this migration in a maintenance window after taking a backup.
-- It is idempotent on MySQL 8 and does not modify application data.
USE permacore_iam;

-- Supports stable active-user pagination. With del_flag fixed, MySQL can walk
-- the index in create_time/id order and fetch only the page IDs before lookup.
SET @has_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
        GROUP BY INDEX_NAME
    ) AS existing_indexes
    WHERE indexed_columns = 'del_flag,create_time,id'
);
SET @migration_sql = IF(
    @has_index = 0,
    'ALTER TABLE sys_user ADD INDEX idx_user_active_created (del_flag, create_time DESC, id DESC)',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- The unique (user_id, role_id) index already covers the forward direction.
-- This reverse index covers role_id -> user_id invalidation queries and also
-- satisfies the role_id foreign key, replacing its auto-created single index.
SET @has_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
        GROUP BY INDEX_NAME
    ) AS existing_indexes
    WHERE indexed_columns = 'role_id,user_id'
);
SET @migration_sql = IF(
    @has_index = 0,
    'ALTER TABLE sys_user_role ADD INDEX idx_user_role_role_user (role_id, user_id)',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_redundant_fk_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
        GROUP BY INDEX_NAME
    ) AS existing_indexes
    WHERE INDEX_NAME = 'fk_user_role_role' AND indexed_columns = 'role_id'
);
SET @migration_sql = IF(
    @has_redundant_fk_index > 0,
    'ALTER TABLE sys_user_role DROP INDEX fk_user_role_role',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- Recursive inheritance walks from descendant_id to ancestor_id. The existing
-- unique (ancestor_id, descendant_id) index covers only the opposite direction.
SET @has_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_inheritance'
        GROUP BY INDEX_NAME
    ) AS existing_indexes
    WHERE indexed_columns = 'descendant_id,ancestor_id'
);
SET @migration_sql = IF(
    @has_index = 0,
    'ALTER TABLE sys_role_inheritance ADD INDEX idx_role_inheritance_desc_anc (descendant_id, ancestor_id)',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_redundant_fk_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_inheritance'
        GROUP BY INDEX_NAME
    ) AS existing_indexes
    WHERE INDEX_NAME = 'fk_role_inheritance_descendant' AND indexed_columns = 'descendant_id'
);
SET @migration_sql = IF(
    @has_redundant_fk_index > 0,
    'ALTER TABLE sys_role_inheritance DROP INDEX fk_role_inheritance_descendant',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- Latest-first login-log paging uses a deterministic id tie-breaker. An ASC
-- variant of the same two columns is also accepted because MySQL can scan it
-- backwards when both requested sort directions are DESC.
SET @has_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log'
        GROUP BY INDEX_NAME
    ) AS existing_indexes
    WHERE indexed_columns = 'login_time,id'
);
SET @migration_sql = IF(
    @has_index = 0,
    'ALTER TABLE sys_login_log ADD INDEX idx_login_time_id (login_time DESC, id DESC)',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- Replaces the old oper_time-only index with a deterministic covering sort
-- key. Keeping both would duplicate the same leftmost index prefix.
SET @has_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log'
        GROUP BY INDEX_NAME
    ) AS existing_indexes
    WHERE indexed_columns = 'oper_time,id'
);
SET @migration_sql = IF(
    @has_index = 0,
    'ALTER TABLE sys_oper_log ADD INDEX idx_oper_time_id (oper_time DESC, id DESC)',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_redundant_oper_time_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log'
        GROUP BY INDEX_NAME
    ) AS existing_indexes
    WHERE INDEX_NAME = 'idx_oper_time' AND indexed_columns = 'oper_time'
);
SET @migration_sql = IF(
    @has_redundant_oper_time_index > 0,
    'ALTER TABLE sys_oper_log DROP INDEX idx_oper_time',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- sys_role_permission already has the FK-created permission_id index. Its only
-- reverse operation is DELETE, which must touch the base rows, so a wider
-- (permission_id, role_id) index would add write cost without a covering-read win.
