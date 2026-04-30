-- =====================================================================
-- V3: 采购模块接口权限初始数据
-- 为 erp-purchase 的 @SaCheckPermission 注解提供对应权限记录
-- =====================================================================

INSERT INTO `sys_permission` (`tenant_id`, `parent_id`, `perm_name`, `perm_code`, `perm_type`,
                               `api_path`, `api_method`, `sort`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
    -- 采购订单菜单（permType=1，前端用，不进入鉴权 Session）
    ('default', 0,    '采购管理',     'purchase',              1, NULL,                    NULL,   1, 1, NOW(), NOW(), 0),
    ('default', NULL, '采购订单',     'purchase:order',         1, NULL,                    NULL,   1, 1, NOW(), NOW(), 0),
    -- 采购订单按钮/接口权限（permType=2 按钮，permType=3 接口）
    ('default', NULL, '采购订单列表', 'purchase:order:list',    2, '/purchase/orders/list', 'GET',  1, 1, NOW(), NOW(), 0),
    ('default', NULL, '采购订单详情', 'purchase:order:query',   2, '/purchase/orders/{id}', 'GET',  2, 1, NOW(), NOW(), 0),
    ('default', NULL, '新建采购订单', 'purchase:order:create',  2, '/purchase/orders',      'POST', 3, 1, NOW(), NOW(), 0),
    ('default', NULL, '确认采购订单', 'purchase:order:confirm', 2, '/purchase/orders/{id}/confirm', 'PUT', 4, 1, NOW(), NOW(), 0),
    ('default', NULL, '取消采购订单', 'purchase:order:cancel',  2, '/purchase/orders/{id}/cancel',  'PUT', 5, 1, NOW(), NOW(), 0);
