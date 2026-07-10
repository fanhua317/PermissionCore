-- ============================================================
-- PermaCore IAM 数据库建表脚本
-- 自动由 Docker 容器首次启动时执行
-- ============================================================

CREATE DATABASE IF NOT EXISTS permacore_iam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE permacore_iam;

-- 全局授权状态单例。角色、权限、继承或 SoD 变化时只递增这一行，避免全表更新用户。
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

-- 1. 部门表
CREATE TABLE IF NOT EXISTS sys_dept (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    parent_id   BIGINT       DEFAULT 0               COMMENT '父部门ID',
    dept_name   VARCHAR(100) NOT NULL                 COMMENT '部门名称',
    dept_path   VARCHAR(255) DEFAULT NULL             COMMENT '部门路径（如：/1/2/3）',
    leader_id   BIGINT       DEFAULT NULL             COMMENT '负责人ID',
    phone       VARCHAR(20)  DEFAULT NULL             COMMENT '联系电话',
    email       VARCHAR(100) DEFAULT NULL             COMMENT '邮箱',
    sort_order  INT          DEFAULT 0                COMMENT '排序',
    status      TINYINT      DEFAULT 1                COMMENT '状态：1-正常 0-停用',
    create_by   BIGINT       DEFAULT NULL             COMMENT '创建人',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       DEFAULT NULL             COMMENT '更新人',
    update_time DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag    TINYINT      DEFAULT 0                COMMENT '删除标志：0-正常 1-删除',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- 2. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL                 COMMENT '用户名',
    password    VARCHAR(200) NOT NULL                 COMMENT '密码（BCrypt加密）',
    nickname    VARCHAR(50)  DEFAULT NULL             COMMENT '昵称',
    email       VARCHAR(100) DEFAULT NULL             COMMENT '邮箱',
    phone       VARCHAR(20)  DEFAULT NULL             COMMENT '手机号',
    dept_id     BIGINT       DEFAULT NULL             COMMENT '部门ID',
    status      TINYINT      DEFAULT 1                COMMENT '状态：1-正常 0-禁用',
    auth_version BIGINT      NOT NULL DEFAULT 0       COMMENT '授权状态版本，每次撤销会话时递增',
    create_by   BIGINT       DEFAULT NULL             COMMENT '创建人',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       DEFAULT NULL             COMMENT '更新人',
    update_time DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark      VARCHAR(500) DEFAULT NULL             COMMENT '备注',
    del_flag    TINYINT      DEFAULT 0                COMMENT '删除标志：0-正常 1-删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_user_active_created (del_flag, create_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 3. 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_key    VARCHAR(100) NOT NULL                 COMMENT '角色标识',
    role_name   VARCHAR(100) NOT NULL                 COMMENT '角色名称',
    role_type   TINYINT      DEFAULT 2                COMMENT '角色类型：1-系统角色 2-自定义角色',
    sort_order  INT          DEFAULT 0                COMMENT '排序',
    status      TINYINT      DEFAULT 1                COMMENT '状态：1-启用 0-禁用',
    create_by   BIGINT       DEFAULT NULL             COMMENT '创建人',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       DEFAULT NULL             COMMENT '更新人',
    update_time DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark      VARCHAR(500) DEFAULT NULL             COMMENT '备注',
    del_flag    TINYINT      DEFAULT 0                COMMENT '删除标志：0-正常 1-删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 4. 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    perm_key      VARCHAR(100) NOT NULL                 COMMENT '权限标识（如：user:add）',
    perm_name     VARCHAR(100) NOT NULL                 COMMENT '权限名称',
    resource_type TINYINT      DEFAULT 1                COMMENT '资源类型：1-菜单 2-按钮/操作 3-API接口',
    resource_id   BIGINT       DEFAULT 0                COMMENT '资源ID',
    parent_id     BIGINT       DEFAULT 0                COMMENT '父权限ID',
    sort_order    INT          DEFAULT 0                COMMENT '排序',
    status        TINYINT      DEFAULT 1                COMMENT '状态：1-启用 0-禁用',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_key (perm_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 5. 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    user_id     BIGINT   NOT NULL                 COMMENT '用户ID',
    role_id     BIGINT   NOT NULL                 COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_role_user (role_id, user_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 6. 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    role_id       BIGINT   NOT NULL                 COMMENT '角色ID',
    permission_id BIGINT   NOT NULL                 COMMENT '权限ID',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_role_perm_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 7. 角色继承关系表（RBAC3）
CREATE TABLE IF NOT EXISTS sys_role_inheritance (
    id            BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    ancestor_id   BIGINT NOT NULL                 COMMENT '祖先角色ID',
    descendant_id BIGINT NOT NULL                 COMMENT '后代角色ID',
    depth         INT    DEFAULT 1                COMMENT '继承深度',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ancestor_descendant (ancestor_id, descendant_id),
    KEY idx_role_inheritance_desc_anc (descendant_id, ancestor_id),
    CONSTRAINT chk_role_inheritance_no_self CHECK (ancestor_id <> descendant_id),
    CONSTRAINT fk_role_inheritance_ancestor FOREIGN KEY (ancestor_id) REFERENCES sys_role (id),
    CONSTRAINT fk_role_inheritance_descendant FOREIGN KEY (descendant_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色继承关系表';

-- 8. 职责分离约束表（RBAC3 SoD）
CREATE TABLE IF NOT EXISTS sys_sod_constraint (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '约束ID',
    constraint_name VARCHAR(200) NOT NULL                 COMMENT '约束名称',
    role_set        VARCHAR(500) NOT NULL                 COMMENT '互斥角色ID数组（JSON格式）',
    constraint_type TINYINT      DEFAULT 1                COMMENT '约束类型：1-静态互斥 2-动态互斥',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职责分离约束表';

-- 9. 登录日志表
CREATE TABLE IF NOT EXISTS sys_login_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    username    VARCHAR(50)  DEFAULT NULL             COMMENT '登录用户名',
    ip_address  VARCHAR(128) DEFAULT NULL             COMMENT '登录IP',
    location    VARCHAR(255) DEFAULT NULL             COMMENT '登录地点',
    browser     VARCHAR(100) DEFAULT NULL             COMMENT '浏览器',
    os          VARCHAR(100) DEFAULT NULL             COMMENT '操作系统',
    login_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    status      TINYINT      DEFAULT 1                COMMENT '登录状态：1-成功 0-失败',
    message     VARCHAR(255) DEFAULT NULL             COMMENT '登录消息',
    PRIMARY KEY (id),
    KEY idx_username (username),
    KEY idx_login_time_id (login_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- 10. 操作日志表
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    title          VARCHAR(100)  DEFAULT NULL             COMMENT '操作标题',
    business_type  TINYINT       DEFAULT 0                COMMENT '业务类型（0-其他 1-新增 2-修改 3-删除）',
    method         VARCHAR(200)  DEFAULT NULL             COMMENT '请求方法',
    request_method VARCHAR(10)   DEFAULT NULL             COMMENT '请求方式（GET/POST）',
    operator_id    BIGINT        DEFAULT NULL             COMMENT '操作人ID',
    operator_name  VARCHAR(50)   DEFAULT NULL             COMMENT '操作人姓名',
    oper_ip        VARCHAR(128)  DEFAULT NULL             COMMENT '操作IP',
    oper_location  VARCHAR(255)  DEFAULT NULL             COMMENT '操作地点',
    oper_param     TEXT          DEFAULT NULL             COMMENT '请求参数',
    json_result    TEXT          DEFAULT NULL             COMMENT '返回结果',
    status         TINYINT       DEFAULT 0                COMMENT '操作状态（0-正常 1-异常）',
    error_msg      VARCHAR(2000) DEFAULT NULL             COMMENT '错误消息',
    oper_time      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    cost_time      BIGINT        DEFAULT 0                COMMENT '耗时（毫秒）',
    PRIMARY KEY (id),
    KEY idx_oper_time_id (oper_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
