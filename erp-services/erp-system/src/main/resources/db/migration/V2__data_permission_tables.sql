-- =====================================================================
-- V2: 数据权限相关表
-- 新增: sys_dept, sys_user_dept, sys_role_dept
-- 修改: sys_user 增加 dept_id 字段
-- =====================================================================

-- 部门表
CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   VARCHAR(64)  NOT NULL                COMMENT '租户ID',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父部门ID，0表示根节点',
    `dept_name`   VARCHAR(100) NOT NULL                COMMENT '部门名称',
    `sort`        INT          NOT NULL DEFAULT 0      COMMENT '显示排序',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1-启用，0-禁用',
    `create_time` DATETIME     NOT NULL                COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL                COMMENT '更新时间',
    `create_by`   BIGINT                               COMMENT '创建人',
    `update_by`   BIGINT                               COMMENT '更新人',
    `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-正常，1-删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_parent` (`tenant_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 用户部门关联表（一用户可属于多部门，primary_dept 通过 sys_user.dept_id 标识）
CREATE TABLE IF NOT EXISTS `sys_user_dept` (
    `id`        BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`   BIGINT  NOT NULL               COMMENT '用户ID',
    `dept_id`   BIGINT  NOT NULL               COMMENT '部门ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_dept` (`user_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户部门关联表';

-- 角色自定义部门表（dataScope=5 时指定的部门范围）
CREATE TABLE IF NOT EXISTS `sys_role_dept` (
    `id`        BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`   BIGINT  NOT NULL               COMMENT '角色ID',
    `dept_id`   BIGINT  NOT NULL               COMMENT '部门ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_dept` (`role_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色自定义数据范围部门表';

-- 为 sys_user 增加主部门字段
ALTER TABLE `sys_user`
    ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT '主部门ID' AFTER `tenant_id`;

-- 更新 sys_role.data_scope 注释（scope=5 为自定义部门）
ALTER TABLE `sys_role`
    MODIFY COLUMN `data_scope` TINYINT DEFAULT NULL
        COMMENT '数据权限范围：1-全部，2-本部门，3-本部门及下级，4-仅本人，5-自定义部门';
