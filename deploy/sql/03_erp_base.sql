-- ============================================================
-- 03_erp_base.sql
-- 基础数据：客户/供应商/物料/仓库/计量单位
-- ============================================================
USE erp_base;

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
-- 计量单位
-- ----------------------------
CREATE TABLE IF NOT EXISTS bas_unit
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT      NOT NULL DEFAULT 0,
    unit_code   VARCHAR(32) NOT NULL,
    unit_name   VARCHAR(64) NOT NULL,
    unit_symbol VARCHAR(16) COMMENT '符号，如 kg, pcs, m',
    status      TINYINT(1) NOT NULL DEFAULT 1,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator     BIGINT,
    updater     BIGINT,
    deleted     TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_unit_code (tenant_id, unit_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '计量单位';

-- ----------------------------
-- 物料主数据
-- ----------------------------
CREATE TABLE IF NOT EXISTS bas_material
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL DEFAULT 0,
    material_code   VARCHAR(64)    NOT NULL COMMENT '物料编码（租户内唯一）',
    material_name   VARCHAR(128)   NOT NULL COMMENT '物料名称',
    material_type   VARCHAR(32)    NOT NULL COMMENT 'RAW原料/WIP半成品/FG成品/PACKAGING包材/CONSUMABLE耗材',
    unit_id         BIGINT         COMMENT '主计量单位ID',
    spec            VARCHAR(255)   COMMENT '规格型号',
    category        VARCHAR(64)    COMMENT '分类',
    standard_cost   DECIMAL(18, 4) COMMENT '标准成本',
    safety_stock    DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '安全库存',
    lead_time_days  INT            NOT NULL DEFAULT 0 COMMENT '采购提前期（天）',
    batch_managed   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否批次管理',
    serial_managed  TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否序列号管理',
    purchase_price  DECIMAL(18, 4) COMMENT '参考采购价',
    status          TINYINT(1)     NOT NULL DEFAULT 1,
    remark          VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_material_code (tenant_id, material_code),
    KEY idx_tenant_id (tenant_id),
    KEY idx_material_type (material_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物料主数据';

-- ----------------------------
-- 客户档案
-- ----------------------------
CREATE TABLE IF NOT EXISTS bas_customer
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT         NOT NULL DEFAULT 0,
    customer_code     VARCHAR(64)    NOT NULL,
    customer_name     VARCHAR(128)   NOT NULL,
    customer_type     VARCHAR(16)    NOT NULL DEFAULT 'ENTERPRISE' COMMENT 'ENTERPRISE企业/INDIVIDUAL个人',
    credit_limit      DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '信用额度',
    payment_term_days INT            NOT NULL DEFAULT 30 COMMENT '账期（天）',
    tax_no            VARCHAR(64)    COMMENT '税号',
    address           VARCHAR(255),
    contact_person    VARCHAR(32),
    contact_phone     VARCHAR(20),
    contact_email     VARCHAR(64),
    bank_name         VARCHAR(64),
    bank_account      VARCHAR(64),
    status            TINYINT(1)     NOT NULL DEFAULT 1,
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator           BIGINT,
    updater           BIGINT,
    deleted           TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_customer_code (tenant_id, customer_code),
    KEY idx_tenant_id (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户档案';

-- ----------------------------
-- 供应商档案
-- ----------------------------
CREATE TABLE IF NOT EXISTS bas_supplier
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT         NOT NULL DEFAULT 0,
    supplier_code     VARCHAR(64)    NOT NULL,
    supplier_name     VARCHAR(128)   NOT NULL,
    supplier_type     VARCHAR(32)    NOT NULL DEFAULT 'RAW_MATERIAL'
        COMMENT 'RAW_MATERIAL原材料/AUXILIARY辅材/SERVICE服务',
    tax_no            VARCHAR(64)    COMMENT '税号',
    payment_term_days INT            NOT NULL DEFAULT 30,
    bank_name         VARCHAR(64),
    bank_account      VARCHAR(64),
    address           VARCHAR(255),
    contact_person    VARCHAR(32),
    contact_phone     VARCHAR(20),
    contact_email     VARCHAR(64),
    status            TINYINT(1)     NOT NULL DEFAULT 1,
    rating            TINYINT(1)     COMMENT '评级 1-5星',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator           BIGINT,
    updater           BIGINT,
    deleted           TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_supplier_code (tenant_id, supplier_code),
    KEY idx_tenant_id (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商档案';

-- ----------------------------
-- 仓库
-- ----------------------------
CREATE TABLE IF NOT EXISTS bas_warehouse
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT      NOT NULL DEFAULT 0,
    warehouse_code     VARCHAR(32) NOT NULL,
    warehouse_name     VARCHAR(64) NOT NULL,
    warehouse_type     VARCHAR(32) NOT NULL DEFAULT 'NORMAL'
        COMMENT 'NORMAL普通/RAW原料/FG成品/TRANSIT在途',
    location_managed   TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否启用库位管理',
    address            VARCHAR(255),
    manager            VARCHAR(32),
    status             TINYINT(1)  NOT NULL DEFAULT 1,
    create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator            BIGINT,
    updater            BIGINT,
    deleted            TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_warehouse_code (tenant_id, warehouse_code),
    KEY idx_tenant_id (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '仓库';

-- 初始化默认数据
INSERT IGNORE INTO bas_unit (id, tenant_id, unit_code, unit_name, unit_symbol)
VALUES
    (1, 0, 'PCS', '件', 'pcs'),
    (2, 0, 'KG',  '千克', 'kg'),
    (3, 0, 'M',   '米', 'm'),
    (4, 0, 'BOX', '箱', 'box'),
    (5, 0, 'SET', '套', 'set');
