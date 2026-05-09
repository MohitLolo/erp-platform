-- ============================================================
-- 09_erp_log.sql
-- 操作日志库：集中收集所有业务服务的用户操作日志（@SaCheckPermission AOP 切面写入）
-- 设计文档：docs/plans/2026-05-08-operation-log-aop-design.md  §7
-- ============================================================

CREATE DATABASE IF NOT EXISTS erp_log
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE erp_log;

-- ----------------------------
-- 操作日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS operation_log
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',

    -- 上下文
    tenant_id       BIGINT       COMMENT '租户ID（可空，平台级操作为 NULL）',
    service_name    VARCHAR(64)  NOT NULL COMMENT '所属服务名（spring.application.name）',
    trace_id        VARCHAR(64)  COMMENT 'SkyWalking / MDC traceId',

    -- 用户
    user_id         BIGINT       COMMENT '操作用户ID（鉴权失败时可空）',
    user_name       VARCHAR(64)  COMMENT '操作用户名（冗余便于查询）',
    client_ip       VARCHAR(64)  COMMENT '客户端 IP（X-Forwarded-For 优先）',
    user_agent      VARCHAR(255) COMMENT 'User-Agent 截断',

    -- 权限
    permission_code VARCHAR(128) NOT NULL COMMENT '权限编码（@SaCheckPermission 值）',
    permission_name VARCHAR(128) COMMENT '权限名称（解析自 sys_permission）',
    permission_module VARCHAR(64) COMMENT '权限所属模块',
    permission_type VARCHAR(32)  COMMENT '权限类型（menu/button/api 等）',

    -- 接口
    http_method     VARCHAR(16)  COMMENT 'HTTP 方法',
    request_uri     VARCHAR(512) COMMENT '请求 URI',
    method_signature VARCHAR(512) COMMENT 'Java 方法签名（class#method）',

    -- 入参 / 出参（JSON，已脱敏）
    request_params  JSON         COMMENT '入参（脱敏后 JSON，按 @LogParam 规则收集）',
    response_data   JSON         COMMENT '返回值（脱敏后 JSON，仅当 @LogResult 标记时记录）',

    -- 结果
    status          VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILURE/DENIED/UNAUTHORIZED',
    error_message   VARCHAR(2000) COMMENT '异常消息（截断 2000 字符）',
    duration_ms     INT          COMMENT '耗时毫秒',

    -- 时间
    occurred_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间（毫秒精度）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',

    PRIMARY KEY (id),

    -- 6 个索引（设计文档 §7）
    KEY idx_user_time     (user_id, occurred_at),
    KEY idx_permission    (permission_code, occurred_at),
    KEY idx_status_time   (status, occurred_at),
    KEY idx_trace         (trace_id),
    KEY idx_occurred      (occurred_at),
    KEY idx_service_time  (service_name, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户操作日志（统一收集，AOP @SaCheckPermission 切面写入）';
