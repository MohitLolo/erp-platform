-- ============================================================
-- 04_erp_sale.sql
-- 销售服务：销售订单/订单行/发货单/发货明细
-- ============================================================
USE erp_sale;

-- Seata undo_log
CREATE TABLE IF NOT EXISTS undo_log
(
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT(11)      NOT NULL,
    log_created   DATETIME(6)  NOT NULL,
    log_modified  DATETIME(6)  NOT NULL,
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------
-- 销售订单主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sale_order
(
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id       BIGINT         NOT NULL COMMENT '租户ID',
    order_no        VARCHAR(32)    NOT NULL COMMENT '订单号（格式：SO+年月日+6位流水）',
    customer_id     BIGINT         NOT NULL COMMENT '客户ID',
    customer_name   VARCHAR(128)   NOT NULL COMMENT '客户名称（冗余存储）',
    order_date      DATE           NOT NULL COMMENT '订单日期',
    delivery_date   DATE           COMMENT '要求交货日期',
    currency        VARCHAR(8)     NOT NULL DEFAULT 'CNY' COMMENT '币种',
    exchange_rate   DECIMAL(10, 6) NOT NULL DEFAULT 1.000000 COMMENT '汇率',
    total_amount    DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '订单金额（含税）',
    tax_amount      DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '税额',
    status          VARCHAR(16)    NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT草稿/CONFIRMED已确认/IN_DELIVERY发货中/DELIVERED已发货/INVOICED已开票/CLOSED已关闭/CANCELLED已取消',
    salesperson_id  BIGINT         COMMENT '销售员ID',
    salesperson_name VARCHAR(32)   COMMENT '销售员姓名',
    warehouse_id    BIGINT         COMMENT '默认发货仓库',
    payment_term_days INT          NOT NULL DEFAULT 30 COMMENT '账期（天）',
    remark          VARCHAR(512),
    version         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_order_no (tenant_id, order_no),
    KEY idx_tenant_id (tenant_id),
    KEY idx_customer_id (customer_id),
    KEY idx_status (status),
    KEY idx_order_date (order_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售订单';

-- ----------------------------
-- 销售订单行
-- ----------------------------
CREATE TABLE IF NOT EXISTS sale_order_item
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    order_id        BIGINT         NOT NULL COMMENT '订单ID',
    order_no        VARCHAR(32)    NOT NULL COMMENT '订单号（冗余）',
    line_no         INT            NOT NULL COMMENT '行号',
    material_id     BIGINT         NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64)    NOT NULL COMMENT '物料编码（冗余）',
    material_name   VARCHAR(128)   NOT NULL COMMENT '物料名称（冗余）',
    quantity        DECIMAL(18, 4) NOT NULL COMMENT '订货数量',
    unit_price      DECIMAL(18, 4) NOT NULL COMMENT '含税单价',
    tax_rate        DECIMAL(8, 4)  NOT NULL DEFAULT 0.13 COMMENT '税率',
    amount          DECIMAL(18, 2) NOT NULL COMMENT '行金额（含税）',
    warehouse_id    BIGINT         COMMENT '发货仓库',
    delivered_qty   DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已发货数量',
    invoiced_qty    DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已开票数量',
    required_date   DATE           COMMENT '行要求交货日期',
    remark          VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_tenant_material (tenant_id, material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售订单行';

-- ----------------------------
-- 发货单
-- ----------------------------
CREATE TABLE IF NOT EXISTS sale_delivery
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT      NOT NULL,
    delivery_no     VARCHAR(32) NOT NULL COMMENT '发货单号（DO+年月日+6位）',
    order_id        BIGINT      NOT NULL COMMENT '来源销售订单ID',
    order_no        VARCHAR(32) NOT NULL,
    customer_id     BIGINT      NOT NULL,
    customer_name   VARCHAR(128) NOT NULL,
    delivery_date   DATE        NOT NULL COMMENT '实际发货日期',
    warehouse_id    BIGINT      NOT NULL COMMENT '发货仓库',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/CONFIRMED/SHIPPED/RECEIVED',
    carrier         VARCHAR(64) COMMENT '承运商',
    tracking_no     VARCHAR(64) COMMENT '运单号',
    remark          VARCHAR(255),
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_delivery_no (tenant_id, delivery_no),
    KEY idx_order_id (order_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售发货单';

-- ----------------------------
-- 发货明细
-- ----------------------------
CREATE TABLE IF NOT EXISTS sale_delivery_item
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    delivery_id     BIGINT         NOT NULL,
    order_item_id   BIGINT         NOT NULL COMMENT '来源订单行ID',
    material_id     BIGINT         NOT NULL,
    material_code   VARCHAR(64)    NOT NULL,
    material_name   VARCHAR(128)   NOT NULL,
    quantity        DECIMAL(18, 4) NOT NULL COMMENT '发货数量',
    unit_price      DECIMAL(18, 4) NOT NULL,
    amount          DECIMAL(18, 2) NOT NULL,
    lot_no          VARCHAR(64)    COMMENT '批次号',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_delivery_id (delivery_id),
    KEY idx_order_item_id (order_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售发货明细';
