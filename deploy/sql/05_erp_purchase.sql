-- ============================================================
-- 05_erp_purchase.sql
-- 采购服务：采购订单/收货单/发票匹配
-- ============================================================
USE erp_purchase;

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
-- 采购申请
-- ----------------------------
CREATE TABLE IF NOT EXISTS pur_request
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    request_no    VARCHAR(32) NOT NULL COMMENT 'PR+年月日+6位',
    request_date  DATE        NOT NULL,
    dept_id       BIGINT      COMMENT '申请部门',
    requester_id  BIGINT      COMMENT '申请人',
    requester_name VARCHAR(32),
    required_date DATE        COMMENT '需求日期',
    status        VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/SUBMITTED/APPROVED/REJECTED/CONVERTED',
    remark        VARCHAR(512),
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator       BIGINT,
    updater       BIGINT,
    deleted       TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_request_no (tenant_id, request_no),
    KEY idx_tenant_id (tenant_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购申请';

-- ----------------------------
-- 采购订单
-- ----------------------------
CREATE TABLE IF NOT EXISTS pur_order
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    order_no        VARCHAR(32)    NOT NULL COMMENT 'PO+年月日+6位',
    supplier_id     BIGINT         NOT NULL COMMENT '供应商ID',
    supplier_name   VARCHAR(128)   NOT NULL,
    order_date      DATE           NOT NULL,
    required_date   DATE           COMMENT '要求到货日期',
    currency        VARCHAR(8)     NOT NULL DEFAULT 'CNY',
    exchange_rate   DECIMAL(10, 6) NOT NULL DEFAULT 1.000000,
    total_amount    DECIMAL(18, 2) NOT NULL DEFAULT 0,
    tax_amount      DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status          VARCHAR(16)    NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/CONFIRMED/IN_RECEIPT/RECEIPTED/INVOICED/CLOSED/CANCELLED',
    warehouse_id    BIGINT         COMMENT '收货仓库',
    payment_term_days INT          NOT NULL DEFAULT 30,
    buyer_id        BIGINT         COMMENT '采购员ID',
    buyer_name      VARCHAR(32),
    remark          VARCHAR(512),
    version         INT            NOT NULL DEFAULT 0,
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_order_no (tenant_id, order_no),
    KEY idx_tenant_id (tenant_id),
    KEY idx_supplier_id (supplier_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购订单';

-- ----------------------------
-- 采购订单行
-- ----------------------------
CREATE TABLE IF NOT EXISTS pur_order_item
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    order_id        BIGINT         NOT NULL,
    order_no        VARCHAR(32)    NOT NULL,
    line_no         INT            NOT NULL,
    material_id     BIGINT         NOT NULL,
    material_code   VARCHAR(64)    NOT NULL,
    material_name   VARCHAR(128)   NOT NULL,
    quantity        DECIMAL(18, 4) NOT NULL,
    unit_price      DECIMAL(18, 4) NOT NULL,
    tax_rate        DECIMAL(8, 4)  NOT NULL DEFAULT 0.13,
    amount          DECIMAL(18, 2) NOT NULL,
    received_qty    DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已收货数量',
    invoiced_qty    DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已开票数量',
    warehouse_id    BIGINT         COMMENT '收货仓库',
    remark          VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_tenant_material (tenant_id, material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购订单行';

-- ----------------------------
-- 收货单
-- ----------------------------
CREATE TABLE IF NOT EXISTS pur_receipt
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    receipt_no    VARCHAR(32) NOT NULL COMMENT 'GR+年月日+6位',
    order_id      BIGINT      NOT NULL,
    order_no      VARCHAR(32) NOT NULL,
    supplier_id   BIGINT      NOT NULL,
    supplier_name VARCHAR(128) NOT NULL,
    receipt_date  DATE        NOT NULL COMMENT '收货日期',
    warehouse_id  BIGINT      NOT NULL COMMENT '入库仓库',
    status        VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/CONFIRMED/QC_PASSED/QC_FAILED/STOCKED',
    qc_result     VARCHAR(16) COMMENT 'PASS/FAIL/PARTIAL',
    remark        VARCHAR(255),
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator       BIGINT,
    updater       BIGINT,
    deleted       TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_receipt_no (tenant_id, receipt_no),
    KEY idx_order_id (order_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购收货单';

-- ----------------------------
-- 收货明细
-- ----------------------------
CREATE TABLE IF NOT EXISTS pur_receipt_item
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    receipt_id      BIGINT         NOT NULL,
    order_item_id   BIGINT         NOT NULL,
    material_id     BIGINT         NOT NULL,
    material_code   VARCHAR(64)    NOT NULL,
    material_name   VARCHAR(128)   NOT NULL,
    received_qty    DECIMAL(18, 4) NOT NULL,
    accepted_qty    DECIMAL(18, 4) COMMENT 'QC 验收通过数量',
    rejected_qty    DECIMAL(18, 4) COMMENT 'QC 拒收数量',
    unit_price      DECIMAL(18, 4) NOT NULL,
    amount          DECIMAL(18, 2) NOT NULL,
    lot_no          VARCHAR(64)    COMMENT '批次号',
    remark          VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_receipt_id (receipt_id),
    KEY idx_order_item_id (order_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收货明细';
