-- Existing databases must run this once before starting the hardened backend.
-- The script is idempotent on MySQL 8 and preserves all existing user data.
USE permacore_iam;

SET @has_auth_version = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'auth_version'
);
SET @migration_sql = IF(
    @has_auth_version = 0,
    'ALTER TABLE sys_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0 COMMENT ''授权状态版本，每次撤销会话时递增'' AFTER status',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- Repair half-applied variants of the column instead of trusting its name alone.
UPDATE sys_user SET auth_version = 0 WHERE auth_version IS NULL;
ALTER TABLE sys_user
    MODIFY COLUMN auth_version BIGINT NOT NULL DEFAULT 0
    COMMENT '授权状态版本，每次撤销会话时递增';

-- Global RBAC mutations use one durable singleton instead of updating every
-- sys_user row. CREATE/INSERT are deliberately idempotent for partially
-- upgraded databases.
CREATE TABLE IF NOT EXISTS sys_authorization_state (
    id                  TINYINT  NOT NULL COMMENT '固定为1的单例ID',
    global_auth_version BIGINT   NOT NULL DEFAULT 0 COMMENT '全局授权版本',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT chk_authorization_state_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局授权状态单例';

INSERT INTO sys_authorization_state (id, global_auth_version)
VALUES (1, 0)
ON DUPLICATE KEY UPDATE id = VALUES(id);

-- Reconcile relation integrity for databases created before the hardened schema.
-- Any orphaned relation makes ALTER fail intentionally; repair the data, then rerun.
SET @has_constraint = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
      AND CONSTRAINT_NAME = 'fk_user_role_user');
SET @migration_sql = IF(@has_constraint = 0,
    'ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_constraint = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
      AND CONSTRAINT_NAME = 'fk_user_role_role');
SET @migration_sql = IF(@has_constraint = 0,
    'ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_constraint = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission'
      AND CONSTRAINT_NAME = 'fk_role_perm_role');
SET @migration_sql = IF(@has_constraint = 0,
    'ALTER TABLE sys_role_permission ADD CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES sys_role (id)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_constraint = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission'
      AND CONSTRAINT_NAME = 'fk_role_perm_permission');
SET @migration_sql = IF(@has_constraint = 0,
    'ALTER TABLE sys_role_permission ADD CONSTRAINT fk_role_perm_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE CASCADE',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_constraint = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_inheritance'
      AND CONSTRAINT_NAME = 'fk_role_inheritance_ancestor');
SET @migration_sql = IF(@has_constraint = 0,
    'ALTER TABLE sys_role_inheritance ADD CONSTRAINT fk_role_inheritance_ancestor FOREIGN KEY (ancestor_id) REFERENCES sys_role (id)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_constraint = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_inheritance'
      AND CONSTRAINT_NAME = 'fk_role_inheritance_descendant');
SET @migration_sql = IF(@has_constraint = 0,
    'ALTER TABLE sys_role_inheritance ADD CONSTRAINT fk_role_inheritance_descendant FOREIGN KEY (descendant_id) REFERENCES sys_role (id)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_constraint = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_inheritance'
      AND CONSTRAINT_NAME = 'chk_role_inheritance_no_self');
SET @migration_sql = IF(@has_constraint = 0,
    'ALTER TABLE sys_role_inheritance ADD CONSTRAINT chk_role_inheritance_no_self CHECK (ancestor_id <> descendant_id)',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
