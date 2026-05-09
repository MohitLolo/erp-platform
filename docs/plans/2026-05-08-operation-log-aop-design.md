# 用户操作日志（AOP）设计

- 作者：erp-platform
- 日期：2026-05-08
- 状态：已评审，待实施

## 1. 背景与目标

### 1.1 背景

ERP 微服务平台已有 9 个业务服务（auth/system/base/inventory/purchase/sale/finance/production/report），用户操作通过 Sa-Token 的 `@SaCheckPermission` 进行权限校验。当前缺少统一的"用户操作日志"能力——开发排查问题、运维追溯异常操作时，没有结构化数据可查。

### 1.2 目标

- **零侵入**：业务 Controller 无需新增任何注解，已有的 `@SaCheckPermission` 自动触发日志记录
- **业务可读**：日志中既存权限编码也存中文描述（如"物流订单导出功能"），方便人工查阅
- **业务无影响**：日志能力故障时，永远不能让业务接口出错或显著变慢
- **集中存储**：9 个服务的日志统一写入 `erp_log` 库，便于跨服务检索

### 1.3 非目标

- 不替代 Prometheus/SkyWalking（这两个解决"指标"和"链路"，本设计解决"业务事件"）
- 不覆盖 erp-gateway（响应式 WebFlux，本设计仅覆盖 Servlet 业务服务）
- 不实现"权限改名实时同步"——10 分钟 TTL 兜底足够；后续可加 MQ 广播

## 2. 关键决策摘要

| # | 决策点 | 选择 | 备注 |
|---|---|---|---|
| 1 | 切点 | `@SaCheckPermission` 注解 | 权限即操作，业务语义天然 |
| 2 | 范围 | 查询也记 | 异步写入承担量级 |
| 3 | 写入路径 | `@Async` 异步线程池 | JVM 崩溃丢少量可接受 |
| 4 | 存储 | 集中库 `erp_log` | 跨服务检索简单 |
| 5 | 跨库写 | dynamic-datasource | 项目已有，零增量成本 |
| 6 | 权限名缓存 | L1 Caffeine + L2 Redis Hash | TTL 兜底，MQ 广播延后 |
| 7 | 脱敏-默认 | 不记请求体，`@LogParam` 显式标注 | 安全优先 |
| 8 | 脱敏-异常 | 异常时记全部入参 | 仍走硬编码黑名单 |
| 9 | 鉴权失败 | 记 `DENIED` 日志 | 安全审计价值 |
| 10 | 模块归属 | `erp-common-log` | 单一模块，自动装配 |
| 11 | 保留策略 | XXL-JOB 定期清理（默认 90 天） | 参数化可配 |

## 3. 整体架构

### 3.1 模块依赖

```
                ┌──────────────────────┐
                │  erp-common-log       │
                └──┬──────┬─────┬──────┘
                   │      │     │
              ┌────▼─┐ ┌──▼──┐ ┌▼─────────┐
              │ auth │ │redis│ │ mybatis   │
              │(注解)│ │(L2) │ │(动态数据源)│
              └──────┘ └─────┘ └───────────┘
```

### 3.2 部署视图

```
                ┌─────────────────────┐
                │   erp-system        │  ◄── 唯一权限维护方
                │  改动权限时：        │
                │  ① 写 sys_permission │
                │  ② 同步 Redis Hash   │
                └────────┬────────────┘
                         │
                         ▼
                ┌────────────────────────┐
                │  Redis (L2)            │
                │  KEY=erp:permission:all│
                │  HASH<code, json>      │
                └────────────────────────┘
                         ▲
        启动全量加载      │      10 分钟 TTL 兜底
                         │
   ┌──────────────┬──────┴──────┬──────────────┐ ... 9 个业务服务
   │ auth         │ base        │ sale         │
   │ Caffeine(L1) │ Caffeine(L1)│ Caffeine(L1) │
   └──────┬───────┴──────┬──────┴──────┬───────┘
          └──────────────┴─────────────┘
                         │
                         ▼
              ┌──────────────────┐
              │  erp_log 库       │
              │  operation_log   │
              └──────────────────┘
```

## 4. 注解契约

### 4.1 隐式触发：`@SaCheckPermission`

业务代码无需改动。已有的 `@SaCheckPermission("logistics:order:export")` 会自动触发日志。

### 4.2 可选注解

| 注解 | 位置 | 作用 |
|---|---|---|
| `@LogParam` | 参数 | 该参数序列化进 `request_params` |
| `@LogParam` | 方法 | 所有参数都记（自动跳过 HttpServletRequest 等） |
| `@LogResult` | 方法 | 记录方法返回值 |
| `@LogIgnore` | 方法 | 该接口完全不记日志 |

### 4.3 自动黑名单（不可关闭）

字段名匹配以下关键字（不区分大小写、嵌套递归）一律替换为 `***`：

```
password, pwd, passwd, secret, token, accessToken, refreshToken,
idCard, idNumber, bankCard, bankAccount, cvv, cvc
```

### 4.4 异常时强制记全参

业务异常（非鉴权异常）发生时，无论是否标注 `@LogParam`，所有入参都进 `request_params`（仍过黑名单）。

## 5. 权限名两级缓存

### 5.1 数据形态

```
Redis:
  KEY  : erp:permission:all
  TYPE : Hash
  FIELD: permission_code (e.g. "logistics:order:export")
  VALUE: JSON {code, name, module, type}

Caffeine (各服务本地):
  Key  : String
  Value: Optional<PermissionMeta>  (Optional 防穿透)
```

### 5.2 写入侧（erp-system）

权限增/改/删的写顺序：**先 DB 后 Redis**。
- 启动时全量同步：`sys_permission` → `HSET erp:permission:all`
- 增改：DB 提交后 `HSET <code> <json>`
- 删除：DB 提交后 `HDEL <code>`
- Redis 失败仅记 ERROR，不回滚 DB

### 5.3 读取侧 `PermissionNameResolver`

```
切面调 resolver.resolve(code)
       │
       ▼
   L1 Caffeine ── miss ──► L2 Redis (HGET) ── miss ──► L3 Feign（默认关闭）
       │ hit                  │ hit                       │
       ▼                      ▼                            ▼
   返回 meta             写回 L1 → 返回             写回 L1+L2 → 返回
                                                    miss → 返回 null（日志只记 code）
```

- L2 调用超时硬性 200ms
- L3 默认关闭（避免反向依赖 erp-system）
- Caffeine `expireAfterWrite=10min`，启动时 `HGETALL` 全量预热

### 5.4 配置项

```yaml
erp:
  operation-log:
    enabled: true
    permission-resolver:
      l1-max-size: 10000
      l1-ttl: 10m
      warmup-on-startup: true
      warmup-timeout: 3s
      feign-fallback: false
      redis-hash-key: erp:permission:all
```

## 6. AOP 切面流程

### 6.1 切点

```java
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // 必须比 Sa-Token 切面更外层
@ConditionalOnProperty(prefix = "erp.operation-log", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class OperationLogAspect {

    @Around("@annotation(cn.dev33.satoken.annotation.SaCheckPermission) "
          + "&& !@annotation(com.erp.common.log.annotation.LogIgnore)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable { ... }
}
```

### 6.2 主流程

```
① 构建 LogContext（同步）
   - userId/userName/tenantId/traceId 从 TenantContextHolder + MDC 取
   - HTTP 上下文：method/uri/ip/ua（@Async 调用场景下 req 可能 null）

② 解析权限名（同步，命中 L1 仅 0.0001ms）
   - SaCheckPermission.value()[0] → permissionCode
   - resolver.resolve(code) → permissionName / module

③ 收集入参（按 §4 规则，存引用不序列化）

④ pjp.proceed() 执行业务
   - 正常：status = SUCCESS
   - NotPermissionException：status = DENIED
   - NotLoginException：status = UNAUTHORIZED
   - 其他 Throwable：status = FAILURE，且强制记全部入参

⑤ finally 收尾
   - 计算 duration
   - 决定是否记 response（@LogResult）
   - writer.submit(ctx)（异步丢任务，不阻塞）
```

### 6.3 安全兜底

切面任何环节抛异常都被 try-catch 吞掉，仅记 logback WARN，**业务方法照常执行**。

### 6.4 异步写入器 `AsyncLogWriter`

```java
ThreadPoolExecutor:
  core=4, max=16, keepalive=60s
  queue=ArrayBlockingQueue(1000)
  rejectionPolicy=DiscardOldestPolicy   // 队列满 → 丢最老的
  threadNameFormat="op-log-%d"
```

异步线程内：序列化 + 脱敏 + `mapper.insert()`，全程 try-catch 不抛。

## 7. 表结构

### 7.1 库

```sql
CREATE DATABASE IF NOT EXISTS erp_log
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 7.2 `operation_log` 表

```sql
CREATE TABLE operation_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at     DATETIME(3)  NOT NULL                COMMENT '发生时间(毫秒)',

    user_id         BIGINT       NULL,
    user_name       VARCHAR(64)  NULL,
    tenant_id       VARCHAR(32)  NULL,

    permission_code VARCHAR(128) NOT NULL                COMMENT '如 logistics:order:export',
    permission_name VARCHAR(200) NULL                    COMMENT '权限中文名快照',
    module          VARCHAR(64)  NULL,

    service_name    VARCHAR(64)  NOT NULL                COMMENT 'spring.application.name',
    trace_id        VARCHAR(64)  NULL,

    http_method     VARCHAR(8)   NULL,
    request_uri     VARCHAR(500) NULL,
    client_ip       VARCHAR(64)  NULL,
    user_agent      VARCHAR(500) NULL,

    request_params  TEXT         NULL                    COMMENT '脱敏后的JSON',
    response_data   TEXT         NULL                    COMMENT '仅 @LogResult',

    status          VARCHAR(16)  NOT NULL                COMMENT 'SUCCESS/FAILURE/DENIED/UNAUTHORIZED',
    duration_ms     INT          NULL,
    exception_class VARCHAR(200) NULL,
    exception_msg   VARCHAR(2000) NULL                   COMMENT '截断',

    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_user_time      (user_id, occurred_at),
    KEY idx_permission     (permission_code, occurred_at),
    KEY idx_status_time    (status, occurred_at),
    KEY idx_trace          (trace_id),
    KEY idx_occurred       (occurred_at),
    KEY idx_service_time   (service_name, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户操作日志';
```

### 7.3 dynamic-datasource 配置

各服务 `application.yml` 追加 `log` 数据源：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        log:
          url: jdbc:mysql://localhost:3306/erp_log?...
          username: ${DB_USERNAME:root}
          password: ${DB_PASSWORD:123456}
          hikari:
            maximum-pool-size: 5
            minimum-idle: 1
```

`OperationLogMapper` 用 `@DS("log")` 切到日志库。

### 7.4 容量预估

```
10 用户 × 200 次/天 = 2,000 条/天 × ~600 B = 1.2 MB/天
年产 ≈ 440 MB
单表 5 年内不需要分区
```

## 8. 故障与降级

### 8.1 故障矩阵

| 故障 | 探测 | 降级行为 | 业务影响 | 数据影响 |
|---|---|---|---|---|
| 切面自身异常 | logback WARN | 业务正常执行 | 无 | 这条丢 |
| L1 预热失败 | logback WARN | 懒加载模式 | 启动后前几分钟略慢 | 无 |
| L2 Redis 超时 | 200ms 硬超时 | 返回 null | 无 | name 字段空 |
| L2 Redis 挂掉 | 持续超时 | 同上 | 无 | name 字段空 |
| 队列满 | metric 计数 | 丢最老的 | 无 | 部分丢 |
| DB 写入失败 | logback WARN | 不重试 | 无 | 这条丢 |
| 整体关闭 | yml 开关 | 切面不生效 | 无 | 全不记录 |

### 8.2 Prometheus 指标

```
op_log_queue_size                  Gauge
op_log_submitted_total             Counter
op_log_inserted_total              Counter
op_log_dropped_total{reason}       Counter
op_log_aspect_duration_seconds     Timer
op_log_db_insert_seconds           Timer
cache_gets_total{cache="permission_l1", result="hit|miss"}  (Caffeine 自动)
```

### 8.3 XXL-JOB 清理

```java
@XxlJob("operationLogCleanupJob")
public void execute() {
    LocalDateTime threshold = now().minusDays(retainDays);  // 默认 90
    while (true) {
        int deleted = mapper.deleteBefore(threshold, 5000);
        if (deleted < 5000) break;
        Thread.sleep(100);
    }
}
```

部署到 erp-system 服务承载该 Job。

## 9. 测试与上线

### 9.1 测试金字塔

- 单元测试 75%：`OperationLogAspect`、`PermissionNameResolver`、`ParamCollector`、`SensitiveMasker`、`AsyncLogWriter`
- 集成测试 5%：`@SpringBootTest` + Testcontainers（MySQL + Redis）
- 手工冒烟：上线 5 分钟内执行 7 项检查

### 9.2 单元测试关键用例

- `shouldNotBreakBusinessIfAspectFails`：切面 NPE 不影响业务（最关键）
- `shouldRecordDeniedOnNotPermissionException`：鉴权失败记录
- `shouldRecordAllParamsWhenException`：异常时记全参
- `shouldDropOldestWhenQueueFull`：队列满策略
- `shouldReturnNullWhenL2Timeout`：Redis 超时不阻塞
- `masksNestedFields`：嵌套对象脱敏

### 9.3 上线策略

```
Step 1：仅 erp-system enabled=true，其他 8 个服务 false（观察 1~3 天）
Step 2：扩展到 auth + base + system（观察 3~7 天）
Step 3：开放给所有 9 个业务服务
Step 4：开启 XXL-JOB 清理任务
```

`erp-common-log` 默认 `enabled=false`，各服务显式 opt-in。

### 9.4 紧急回退

改 yml `enabled: false` → 重启服务 → 业务恢复 → 再排查。

### 9.5 验收标准

- 业务接口 P99 增加 < 1ms
- Caffeine 命中率 > 95%
- 切面/Redis/DB 任一故障，业务零影响（混沌测试）
- 正常请求/鉴权失败/业务异常 100% 记录
- 黑名单字段不会泄漏

## 10. 后续演进

| 时机 | 演进项 | 备注 |
|---|---|---|
| 业务有"权限改名秒级生效"诉求 | 加 MQ 广播刷缓存 | 复用提交 c8930c1 的事件机制 |
| 单表 > 5000 万行 | 按月分区 | `PARTITION BY RANGE (YEAR_MONTH(occurred_at))` |
| 查询变慢/单库 > 100GB | 迁 Doris/ClickHouse | deploy/doris 已铺垫 |
| 网关层日志 | 新增 `ReactiveOperationLogFilter` | WebFlux 单独处理 |

## 11. 附录：实施模块清单

```
erp-commons/erp-common-log/
├── annotation/
│   ├── LogParam.java
│   ├── LogResult.java
│   └── LogIgnore.java
├── aspect/
│   └── OperationLogAspect.java
├── resolver/
│   ├── PermissionNameResolver.java
│   ├── CaffeineRedisPermissionResolver.java
│   └── PermissionMeta.java
├── masker/
│   └── SensitiveMasker.java
├── writer/
│   ├── AsyncLogWriter.java
│   └── LogContext.java
├── entity/
│   └── OperationLog.java
├── mapper/
│   └── OperationLogMapper.java
├── job/
│   └── OperationLogCleanupJob.java
├── config/
│   ├── OperationLogProperties.java
│   ├── OperationLogAutoConfiguration.java
│   └── PermissionResolverAutoConfiguration.java
└── filter/
    └── MdcContextFilter.java                (已存在)

erp-services/erp-system/
└── permission/
    └── PermissionRedisSyncService.java      (新增：维护 Redis Hash)

sql/erp_log/
├── V1_0_0__create_database.sql
└── V1_0_0__create_operation_log.sql
```
