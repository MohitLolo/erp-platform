-- ============================================================
-- 02_erp_system.sql
-- 系统服务：租户、用户、角色、权限
-- undo_log：Seata AT 模式必须，每个有写操作的库都要建
-- ============================================================
USE erp_system;

-- ----------------------------
-- Seata undo_log（AT 模式）
-- ----------------------------
CREATE TABLE IF NOT EXISTS undo_log
(
    branch_id     BIGINT       NOT NULL COMMENT 'branch transaction id',
    xid           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    context       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    rollback_info LONGBLOB     NOT NULL COMMENT 'rollback info',
    log_status    INT(11)      NOT NULL COMMENT '0:normal status,1:defense status',
    log_created   DATETIME(6)  NOT NULL COMMENT 'create datetime',
    log_modified  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AT transaction mode undo table';

-- ----------------------------
-- 租户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_tenant
(
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '租户ID',
    tenant_code    VARCHAR(32) NOT NULL COMMENT '租户编码（唯一）',
    tenant_name    VARCHAR(64) NOT NULL COMMENT '租户名称',
    schema_name    VARCHAR(64) NOT NULL COMMENT '租户 Schema 名称（如 erp_tenant_001）',
    contact_person VARCHAR(32) COMMENT '联系人',
    contact_phone  VARCHAR(20) COMMENT '联系电话',
    contact_email  VARCHAR(64) COMMENT '联系邮箱',
    expire_time    DATETIME COMMENT '到期时间，NULL 表示永久',
    status         TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1:启用 0:禁用',
    remark         VARCHAR(255) COMMENT '备注',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator        BIGINT COMMENT '创建人ID',
    updater        BIGINT COMMENT '更新人ID',
    deleted        TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户表';

-- ----------------------------
-- 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    tenant_id   BIGINT      NOT NULL COMMENT '所属租户ID',
    username    VARCHAR(64) NOT NULL COMMENT '用户名（租户内唯一）',
    password    VARCHAR(64) NOT NULL COMMENT 'MD5 密码',
    real_name   VARCHAR(32) COMMENT '真实姓名',
    mobile      VARCHAR(20) COMMENT '手机号',
    email       VARCHAR(64) COMMENT '邮箱',
    avatar      VARCHAR(255) COMMENT '头像URL',
    dept_id     BIGINT COMMENT '所属部门ID',
    status      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1:启用 0:禁用',
    last_login_time DATETIME COMMENT '最后登录时间',
    last_login_ip   VARCHAR(50) COMMENT '最后登录IP',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator     BIGINT COMMENT '创建人ID',
    updater     BIGINT COMMENT '更新人ID',
    deleted     TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    KEY idx_tenant_id (tenant_id),
    KEY idx_mobile (mobile)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ----------------------------
-- 角色表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    tenant_id   BIGINT      NOT NULL COMMENT '所属租户ID',
    role_code   VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name   VARCHAR(64) NOT NULL COMMENT '角色名称',
    data_scope  TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '数据权限：1-全部 2-本部门 3-部门及以下 4-本人',
    status      TINYINT(1) NOT NULL DEFAULT 1,
    sort_order  INT         NOT NULL DEFAULT 0,
    remark      VARCHAR(255),
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator     BIGINT,
    updater     BIGINT,
    deleted     TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_role_code (tenant_id, role_code),
    KEY idx_tenant_id (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ----------------------------
-- 权限（菜单+按钮+接口）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_permission
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点ID，0=根节点',
    perm_code   VARCHAR(128) NOT NULL COMMENT '权限编码，如 sale:order:create',
    perm_name   VARCHAR(64)  NOT NULL COMMENT '权限名称',
    perm_type   TINYINT(1)  NOT NULL COMMENT '1-菜单 2-按钮 3-接口',
    route_path  VARCHAR(255) COMMENT '前端路由路径',
    component   VARCHAR(255) COMMENT '前端组件路径',
    api_path    VARCHAR(255) COMMENT '接口路径（用于接口级鉴权）',
    api_method  VARCHAR(10)  COMMENT 'GET/POST/PUT/DELETE',
    icon        VARCHAR(64)  COMMENT '图标',
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT(1)  NOT NULL DEFAULT 1,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限（菜单/按钮/接口）';

-- ----------------------------
-- 用户-角色关联
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_role
(
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关联';

-- ----------------------------
-- 角色-权限关联
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role_permission
(
    id            BIGINT NOT NULL AUTO_INCREMENT,
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色权限关联';

-- ----------------------------
-- 部门表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_dept
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT      NOT NULL,
    parent_id   BIGINT      NOT NULL DEFAULT 0,
    dept_name   VARCHAR(64) NOT NULL,
    ancestors   VARCHAR(512) COMMENT '祖先路径，如 0,1,2',
    sort_order  INT         NOT NULL DEFAULT 0,
    leader      VARCHAR(32) COMMENT '负责人',
    phone       VARCHAR(20),
    status      TINYINT(1) NOT NULL DEFAULT 1,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_parent (tenant_id, parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门表';

-- ----------------------------
-- 初始化数据：默认超管租户 & 用户
-- ----------------------------
INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, schema_name, status)
VALUES (1, 'SYSTEM', '平台超级租户', 'erp_system', 1);

-- 默认超管用户 admin，密码 Admin@123 的 MD5：1b1fce41f13e4b5a4b0b5a6d4f7c8e2a（请上线前修改）
-- 实际 MD5(Admin@123) = 需运行时生成，此处用占位符说明
INSERT IGNORE INTO sys_user (id, tenant_id, username, password, real_name, status)
VALUES (1, 1, 'admin', MD5('Admin@123'), '超级管理员', 1);

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, status)
VALUES (1, 1, 'SUPER_ADMIN', '超级管理员', 1, 1);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 初始化核心权限节点（部分示例）
INSERT IGNORE INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order)
VALUES
    (1,  0, 'system',             '系统管理',       1, 1),
    (2,  1, 'system:user',        '用户管理',       1, 1),
    (3,  2, 'system:user:list',   '用户列表',       2, 1),
    (4,  2, 'system:user:create', '新增用户',       2, 2),
    (5,  2, 'system:user:update', '修改用户',       2, 3),
    (6,  2, 'system:user:delete', '删除用户',       2, 4),
    (10, 0, 'sale',               '销售管理',       1, 2),
    (11, 10,'sale:order',         '销售订单',       1, 1),
    (12, 11,'sale:order:create',  '创建销售订单',   2, 1),
    (13, 11,'sale:order:query',   '查询销售订单',   2, 2),
    (14, 11,'sale:order:cancel',  '取消销售订单',   2, 3),
    (20, 0, 'purchase',           '采购管理',       1, 3),
    (21, 20,'purchase:order',     '采购订单',       1, 1),
    (22, 21,'purchase:order:create','创建采购订单', 2, 1),
    (30, 0, 'inventory',          '库存管理',       1, 4),
    (31, 30,'inventory:stock',    '库存台账',       1, 1),
    (32, 31,'inventory:stock:query','库存查询',     2, 1),
    (40, 0, 'finance',            '财务管理',       1, 5),
    (41, 40,'finance:receivable', '应收管理',       1, 1),
    (50, 0, 'production',         '生产管理',       1, 6),
    (51, 50,'production:workorder','工单管理',      1, 1),
    (60, 0, 'report',             '报表中心',       1, 7),
    (61, 60,'report:sale',        '销售报表',       2, 1);

-- 超管拥有所有权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;
