-- =====================================================================
-- V4: 列级数据权限
-- 1. sys_permission 表新增 field_name 字段（perm_type=4 列权限专用）
-- 2. 插入采购模块示例列权限记录（仅供参考，实际按业务需求维护）
-- =====================================================================

-- 新增 field_name 字段
ALTER TABLE `sys_permission`
    ADD COLUMN `field_name` VARCHAR(128) NULL
    COMMENT '字段名（perm_type=4 列权限专用，对应 VO 中 @ColumnPermission 保护的字段名；其他类型为 NULL）'
    AFTER `api_method`;

-- =====================================================================
-- 示例：采购订单列权限
-- 将以下记录的 parent_id 替换为实际 sys_permission 表中"采购订单"菜单的 id
-- =====================================================================

-- INSERT INTO `sys_permission`
--     (`tenant_id`, `parent_id`, `perm_name`, `perm_code`, `perm_type`,
--      `field_name`, `sort_order`, `status`, `create_time`, `update_time`, `deleted`)
-- VALUES
--     ('default', #{purchase_order_menu_id}, '查看采购成本价',
--      'purchase:order:view_cost',   4, 'unitCost', 1, 1, NOW(), NOW(), 0),
--     ('default', #{purchase_order_menu_id}, '查看采购利润',
--      'purchase:order:view_profit', 4, 'profit',   2, 1, NOW(), NOW(), 0);
