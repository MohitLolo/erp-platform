-- ============================================================
-- 08_erp_production.sql
-- 生产服务：BOM / 工单 / 报工 / 生产领料
-- ============================================================
USE erp_production;

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
-- BOM 主表（物料清单头）
-- ----------------------------
CREATE TABLE IF NOT EXISTS pro_bom
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    material_id     BIGINT         NOT NULL COMMENT '成品/半成品物料ID',
    material_code   VARCHAR(64)    NOT NULL,
    material_name   VARCHAR(128)   NOT NULL,
    version         VARCHAR(16)    NOT NULL DEFAULT 'V1' COMMENT 'BOM版本',
    base_qty        DECIMAL(18, 4) NOT NULL DEFAULT 1 COMMENT '基础数量（生产多少个该成品的BOM）',
    status          VARCHAR(16)    NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/ACTIVE/OBSOLETE',
    effective_from  DATE           COMMENT '生效日期',
    effective_to    DATE           COMMENT '失效日期',
    remark          VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_version (tenant_id, material_id, version),
    KEY idx_tenant_material (tenant_id, material_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BOM主表';

-- ----------------------------
-- BOM 行（用料明细）
-- ----------------------------
CREATE TABLE IF NOT EXISTS pro_bom_item
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    bom_id          BIGINT         NOT NULL,
    line_no         INT            NOT NULL,
    material_id     BIGINT         NOT NULL COMMENT '子件物料ID',
    material_code   VARCHAR(64)    NOT NULL,
    material_name   VARCHAR(128)   NOT NULL,
    quantity        DECIMAL(18, 6) NOT NULL COMMENT '用量',
    wastage_rate    DECIMAL(8, 4)  NOT NULL DEFAULT 0 COMMENT '损耗率（0.05 = 5%）',
    item_type       VARCHAR(16)    NOT NULL DEFAULT 'MATERIAL'
        COMMENT 'MATERIAL物料/PHANTOM虚项',
    remark          VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_bom_id (bom_id),
    KEY idx_material_id (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BOM行（用料明细）';

-- ----------------------------
-- 生产工单
-- ----------------------------
CREATE TABLE IF NOT EXISTS pro_work_order
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    work_order_no   VARCHAR(32)    NOT NULL COMMENT 'WO+年月日+6位',
    material_id     BIGINT         NOT NULL COMMENT '成品物料ID',
    material_code   VARCHAR(64)    NOT NULL,
    material_name   VARCHAR(128)   NOT NULL,
    bom_id          BIGINT         NOT NULL COMMENT '使用的BOM',
    warehouse_id    BIGINT         COMMENT '完工入库仓库',
    plan_qty        DECIMAL(18, 4) NOT NULL COMMENT '计划数量',
    completed_qty   DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '完工数量',
    scrap_qty       DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '报废数量',
    plan_start_time DATETIME       COMMENT '计划开始时间',
    plan_end_time   DATETIME       COMMENT '计划完工时间',
    actual_start_time DATETIME     COMMENT '实际开始时间',
    actual_end_time   DATETIME     COMMENT '实际完工时间',
    status          VARCHAR(16)    NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/RELEASED已下达/IN_PROGRESS生产中/COMPLETED已完工/CLOSED已关闭',
    source_type     VARCHAR(16)    COMMENT 'SALE_ORDER销售驱动/MRP计划驱动/MANUAL手工',
    source_no       VARCHAR(64)    COMMENT '来源单号（销售订单号/计划号）',
    priority        INT            NOT NULL DEFAULT 50 COMMENT '优先级，值越小优先级越高',
    remark          VARCHAR(512),
    version         INT            NOT NULL DEFAULT 0,
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_wo_no (tenant_id, work_order_no),
    KEY idx_tenant_id (tenant_id),
    KEY idx_material_id (material_id),
    KEY idx_status (status),
    KEY idx_plan_end_time (plan_end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '生产工单';

-- ----------------------------
-- 生产报工（完工汇报）
-- ----------------------------
CREATE TABLE IF NOT EXISTS pro_work_report
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    work_order_id   BIGINT         NOT NULL,
    work_order_no   VARCHAR(32)    NOT NULL,
    report_date     DATE           NOT NULL COMMENT '报工日期',
    completed_qty   DECIMAL(18, 4) NOT NULL COMMENT '本次完工数量',
    scrap_qty       DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '本次报废数量',
    operator_id     BIGINT         COMMENT '操作员',
    workstation     VARCHAR(64)    COMMENT '工作站/工序',
    remark          VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator         BIGINT,
    PRIMARY KEY (id),
    KEY idx_work_order_id (work_order_id),
    KEY idx_report_date (report_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '生产报工';

-- ----------------------------
-- 生产领料单
-- ----------------------------
CREATE TABLE IF NOT EXISTS pro_material_issue
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT      NOT NULL,
    issue_no        VARCHAR(32) NOT NULL COMMENT 'MI+年月日+6位',
    work_order_id   BIGINT      NOT NULL,
    work_order_no   VARCHAR(32) NOT NULL,
    warehouse_id    BIGINT      NOT NULL COMMENT '领料仓库',
    issue_date      DATE        NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/CONFIRMED/STOCKED_OUT',
    remark          VARCHAR(255),
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_issue_no (tenant_id, issue_no),
    KEY idx_work_order_id (work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '生产领料单';

-- ----------------------------
-- 领料明细
-- ----------------------------
CREATE TABLE IF NOT EXISTS pro_material_issue_item
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    issue_id        BIGINT         NOT NULL,
    material_id     BIGINT         NOT NULL,
    material_code   VARCHAR(64)    NOT NULL,
    material_name   VARCHAR(128)   NOT NULL,
    plan_qty        DECIMAL(18, 4) NOT NULL COMMENT '计划领料数量（按BOM计算）',
    actual_qty      DECIMAL(18, 4) NOT NULL COMMENT '实际领料数量',
    unit_cost       DECIMAL(18, 4) COMMENT '领料单位成本',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_issue_id (issue_id),
    KEY idx_material_id (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '领料明细';
