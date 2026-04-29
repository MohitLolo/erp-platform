-- ============================================================
-- 07_erp_finance.sql
-- 财务服务：应收/应付/总账凭证/成本
-- ============================================================
USE erp_finance;

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
-- 应收账款主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS fin_receivable
(
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id        BIGINT         NOT NULL,
    receivable_no    VARCHAR(32)    NOT NULL COMMENT 'AR+年月日+6位',
    source_no        VARCHAR(64)    NOT NULL COMMENT '来源单号（幂等键）',
    source_type      VARCHAR(32)    NOT NULL COMMENT 'SALE_ORDER/SALE_DELIVERY/MANUAL',
    customer_id      BIGINT         NOT NULL COMMENT '客户ID',
    customer_name    VARCHAR(128)   NOT NULL,
    currency         VARCHAR(8)     NOT NULL DEFAULT 'CNY',
    exchange_rate    DECIMAL(10, 6) NOT NULL DEFAULT 1.000000,
    amount           DECIMAL(18, 2) NOT NULL COMMENT '应收金额（本币）',
    received_amount  DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '已收金额',
    unreceived_amount DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '未收金额',
    due_date         DATE           COMMENT '到期日',
    status           VARCHAR(16)    NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING待收/PARTIAL部分收/SETTLED已结清/OVERDUE逾期/BAD_DEBT坏账',
    tax_amount       DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark           VARCHAR(255),
    version          INT            NOT NULL DEFAULT 0,
    create_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator          BIGINT,
    updater          BIGINT,
    deleted          TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_receivable_no (tenant_id, receivable_no),
    UNIQUE KEY uk_source_no (tenant_id, source_no) COMMENT '幂等约束',
    KEY idx_tenant_id (tenant_id),
    KEY idx_customer_id (customer_id),
    KEY idx_status (status),
    KEY idx_due_date (due_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应收账款';

-- ----------------------------
-- 收款记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS fin_receipt
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT         NOT NULL,
    receipt_no      VARCHAR(32)    NOT NULL COMMENT 'RC+年月日+6位',
    receivable_id   BIGINT         NOT NULL COMMENT '应收账款ID',
    customer_id     BIGINT         NOT NULL,
    customer_name   VARCHAR(128)   NOT NULL,
    receipt_date    DATE           NOT NULL COMMENT '收款日期',
    amount          DECIMAL(18, 2) NOT NULL COMMENT '本次收款金额',
    payment_method  VARCHAR(32)    COMMENT '收款方式：BANK_TRANSFER/ALIPAY/WECHAT/CASH/CHECK',
    bank_ref        VARCHAR(64)    COMMENT '银行流水号',
    status          VARCHAR(16)    NOT NULL DEFAULT 'CONFIRMED',
    remark          VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    updater         BIGINT,
    deleted         TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_receipt_no (tenant_id, receipt_no),
    KEY idx_receivable_id (receivable_id),
    KEY idx_receipt_date (receipt_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收款记录';

-- ----------------------------
-- 应付账款
-- ----------------------------
CREATE TABLE IF NOT EXISTS fin_payable
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT         NOT NULL,
    payable_no        VARCHAR(32)    NOT NULL COMMENT 'AP+年月日+6位',
    source_no         VARCHAR(64)    NOT NULL COMMENT '来源采购单号（幂等键）',
    source_type       VARCHAR(32)    NOT NULL COMMENT 'PURCHASE_ORDER/PURCHASE_RECEIPT/MANUAL',
    supplier_id       BIGINT         NOT NULL,
    supplier_name     VARCHAR(128)   NOT NULL,
    currency          VARCHAR(8)     NOT NULL DEFAULT 'CNY',
    exchange_rate     DECIMAL(10, 6) NOT NULL DEFAULT 1.000000,
    amount            DECIMAL(18, 2) NOT NULL,
    paid_amount       DECIMAL(18, 2) NOT NULL DEFAULT 0,
    unpaid_amount     DECIMAL(18, 2) NOT NULL DEFAULT 0,
    due_date          DATE,
    status            VARCHAR(16)    NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/PARTIAL/SETTLED/OVERDUE',
    tax_amount        DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark            VARCHAR(255),
    version           INT            NOT NULL DEFAULT 0,
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator           BIGINT,
    updater           BIGINT,
    deleted           TINYINT(1)     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_payable_no (tenant_id, payable_no),
    UNIQUE KEY uk_source_no (tenant_id, source_no),
    KEY idx_tenant_id (tenant_id),
    KEY idx_supplier_id (supplier_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应付账款';

-- ----------------------------
-- 总账凭证（会计分录）
-- ----------------------------
CREATE TABLE IF NOT EXISTS fin_voucher
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT      NOT NULL,
    voucher_no      VARCHAR(32) NOT NULL COMMENT '凭证号',
    period          VARCHAR(7)  NOT NULL COMMENT '会计期间 YYYY-MM',
    voucher_date    DATE        NOT NULL COMMENT '凭证日期',
    voucher_type    VARCHAR(16) NOT NULL COMMENT 'RECEIPT收/PAYMENT付/TRANSFER转/ADJUST调整',
    source_no       VARCHAR(64) COMMENT '来源单号',
    summary         VARCHAR(255) COMMENT '摘要',
    total_debit     DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '借方合计',
    total_credit    DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '贷方合计',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/POSTED/REVERSED',
    posted_by       BIGINT      COMMENT '过账人',
    posted_time     DATETIME    COMMENT '过账时间',
    remark          VARCHAR(255),
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator         BIGINT,
    deleted         TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_voucher_no (tenant_id, voucher_no),
    KEY idx_period (period),
    KEY idx_voucher_date (voucher_date),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '总账凭证';

-- ----------------------------
-- 凭证分录行
-- ----------------------------
CREATE TABLE IF NOT EXISTS fin_voucher_item
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    voucher_id      BIGINT         NOT NULL,
    line_no         INT            NOT NULL,
    account_code    VARCHAR(32)    NOT NULL COMMENT '科目编码',
    account_name    VARCHAR(64)    NOT NULL COMMENT '科目名称',
    debit_amount    DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '借方金额',
    credit_amount   DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '贷方金额',
    currency        VARCHAR(8)     NOT NULL DEFAULT 'CNY',
    summary         VARCHAR(255),
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_voucher_id (voucher_id),
    KEY idx_account_code (account_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '凭证分录行';
