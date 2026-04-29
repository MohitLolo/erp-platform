-- ============================================================
-- 06_erp_inventory.sql
-- 库存服务：库存台账、库存锁定记录、库存流水
-- ============================================================
USE erp_inventory;

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
-- 库存台账（一个物料在一个仓库的库存快照）
-- ----------------------------
CREATE TABLE IF NOT EXISTS inv_stock
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    warehouse_id    BIGINT         NOT NULL COMMENT '仓库ID',
    material_id     BIGINT         NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64)    NOT NULL COMMENT '物料编码（冗余，查询用）',
    on_hand_qty     DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '在库数量（实物）',
    available_qty   DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '可用数量=在库-锁定',
    locked_qty      DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '锁定数量（销售预占）',
    in_transit_qty  DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '在途数量（已下采购未到货）',
    safety_stock    DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '安全库存',
    version         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_material (tenant_id, warehouse_id, material_id),
    KEY idx_tenant_material (tenant_id, material_id),
    KEY idx_material_code (material_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存台账';

-- ----------------------------
-- 库存锁定记录（销售/生产预占的库存明细）
-- ----------------------------
CREATE TABLE IF NOT EXISTS inv_stock_lock
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    warehouse_id    BIGINT         NOT NULL,
    material_id     BIGINT         NOT NULL,
    biz_no          VARCHAR(128)   NOT NULL COMMENT '业务单号（幂等键）',
    biz_type        VARCHAR(32)    NOT NULL COMMENT 'SALE_ORDER/WORK_ORDER/MANUAL',
    lock_qty        DECIMAL(18, 4) NOT NULL COMMENT '锁定数量',
    lock_status     VARCHAR(16)    NOT NULL DEFAULT 'LOCKED'
        COMMENT 'LOCKED已锁定/RELEASED已释放/CONSUMED已核销',
    lock_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '锁定时间',
    release_time    DATETIME       COMMENT '释放/核销时间',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_no_material (biz_no, material_id) COMMENT '幂等约束',
    KEY idx_tenant_warehouse_material (tenant_id, warehouse_id, material_id),
    KEY idx_biz_no (biz_no),
    KEY idx_lock_status (lock_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存锁定记录';

-- ----------------------------
-- 库存流水（出入库记录）
-- ----------------------------
CREATE TABLE IF NOT EXISTS inv_transaction
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    transaction_no  VARCHAR(32)    NOT NULL COMMENT '流水号',
    warehouse_id    BIGINT         NOT NULL,
    material_id     BIGINT         NOT NULL,
    material_code   VARCHAR(64)    NOT NULL,
    transaction_type VARCHAR(32)   NOT NULL
        COMMENT 'IN_PURCHASE入/OUT_SALE出/IN_RETURN退/OUT_RETURN采购退/IN_TRANSFER调入/OUT_TRANSFER调出/ADJ盘点调整/IN_PRODUCTION完工入/OUT_PRODUCTION生产领料',
    quantity        DECIMAL(18, 4) NOT NULL COMMENT '正数=入库，负数=出库',
    unit_cost       DECIMAL(18, 4) COMMENT '单位成本',
    total_cost      DECIMAL(18, 2) COMMENT '总成本',
    biz_no          VARCHAR(64)    COMMENT '来源单号',
    biz_type        VARCHAR(32)    COMMENT '来源类型',
    operator_id     BIGINT         COMMENT '操作人',
    lot_no          VARCHAR(64)    COMMENT '批次号',
    remark          VARCHAR(255),
    transaction_time DATETIME      NOT NULL COMMENT '发生时间',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transaction_no (tenant_id, transaction_no),
    KEY idx_tenant_warehouse_material (tenant_id, warehouse_id, material_id),
    KEY idx_transaction_type (transaction_type),
    KEY idx_transaction_time (transaction_time),
    KEY idx_biz_no (biz_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存流水';

-- ----------------------------
-- 库存盘点单
-- ----------------------------
CREATE TABLE IF NOT EXISTS inv_count
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    count_no      VARCHAR(32) NOT NULL COMMENT 'IC+年月日+6位',
    warehouse_id  BIGINT      NOT NULL,
    count_date    DATE        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/IN_PROGRESS/COMPLETED/CANCELLED',
    count_type    VARCHAR(16) NOT NULL DEFAULT 'FULL'
        COMMENT 'FULL全盘/CYCLE循环盘',
    remark        VARCHAR(255),
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator       BIGINT,
    updater       BIGINT,
    deleted       TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_count_no (tenant_id, count_no),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存盘点单';
