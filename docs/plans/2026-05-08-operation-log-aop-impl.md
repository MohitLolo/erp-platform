# 用户操作日志（AOP）实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `erp-common-log` 实现基于 `@SaCheckPermission` 注解的用户操作日志统一收集能力，集中写入 `erp_log.operation_log` 表；erp-system 维护权限名 Redis Hash 缓存；9 个业务服务通过引入 starter + 配置 opt-in 启用。

**Architecture:** 两级缓存（Caffeine L1 + Redis Hash L2）解析权限名 → AOP 切面（`@Around` 切 `@SaCheckPermission`）构造 LogContext → 异步线程池写入 `operation_log` 表（`@DS("log")` 多数据源） → XXL-JOB 按保留期清理。设计文档：`docs/plans/2026-05-08-operation-log-aop-design.md`。

**Tech Stack:** Spring AOP、Sa-Token 1.40、Caffeine 3.x（Spring Boot 默认引入）、Redisson、MyBatis-Plus、dynamic-datasource 4.3、micrometer、XXL-JOB 3.3、Testcontainers。

---

## 优先级说明

- **P0**：必须完成，否则功能不可用
- **P1**：增强项，影响可观测性 / 可维护性，但不阻塞核心能力
- **P2**：可选项，未来演进时再补

## 依赖关系简图

```
[基础设施]
  T01 (库表) ──┐
  T02 (Maven) ──┼──► [核心组件]
                │      T03 (注解) ───┐
                │      T04 (entity/mapper) ──┐
                │      T05 (脱敏) ──┐         │
                │      T06 (缓存) ──┼─────────┼──► T08 (切面)
                │      T07 (异步写) ─┘         │       │
                │                              │       ▼
                │                              │   T09 (自动配置)
                │                              │       │
                ├──► T10 (system 同步 Redis) ──┤       │
                │                              │       ▼
                └──────────────────────────────┴──► T11 (集成测试)
                                                         │
                                                         ▼
                                                   T12 (灰度先行: erp-system 接入)
                                                         │
                                                         ▼
                                                   T13 (扩展 auth/base)
                                                         │
                                                         ▼
                                                   T14 (全量推广 9 服务)
                                                         │
                                                         ├──► T15 (清理 Job, P1)
                                                         ├──► T16 (Prometheus 指标, P1)
                                                         ├──► T17 (Reactive 网关支持, P2)
                                                         └──► T18 (MQ 广播刷缓存, P2)
```

---

## TodoList

- [ ] **T01** [P0] 创建 `erp_log` 库与 `operation_log` 表
- [ ] **T02** [P0] 创建 `erp-common-log` 模块依赖与自动装配骨架
- [ ] **T03** [P0] 实现注解契约（`@LogParam` / `@LogResult` / `@LogIgnore`）
- [ ] **T04** [P0] 实现日志实体与 Mapper（`OperationLog` / `OperationLogMapper`，`@DS("log")`）
- [ ] **T05** [P0] 实现敏感字段脱敏器（`SensitiveMasker` + 硬编码黑名单）
- [ ] **T06** [P0] 实现两级缓存权限解析器（`PermissionNameResolver`，L1 Caffeine + L2 Redis）
- [ ] **T07** [P0] 实现异步写入器（`AsyncLogWriter` + ThreadPoolExecutor + DiscardOldest）
- [ ] **T08** [P0] 实现切面（`OperationLogAspect`，覆盖 SUCCESS/FAILURE/DENIED/UNAUTHORIZED）
- [ ] **T09** [P0] 实现自动配置（`OperationLogAutoConfiguration` + `OperationLogProperties`）
- [ ] **T10** [P0] erp-system 维护 Redis Hash（`PermissionRedisSyncService`，启动全量 + CRUD 同步）
- [ ] **T11** [P0] 集成测试（Testcontainers + MySQL + Redis，端到端验证）
- [ ] **T12** [P0] 灰度先行：erp-system 接入并启用，观察 1~3 天
- [ ] **T13** [P0] 扩展到 erp-auth、erp-base
- [ ] **T14** [P0] 全量推广到剩余 6 个业务服务
- [ ] **T15** [P1] XXL-JOB 清理任务（`OperationLogCleanupJob`）
- [ ] **T16** [P1] Prometheus 指标接入（队列水位、命中率、丢弃数）
- [ ] **T17** [P2] Reactive 网关日志（`ReactiveOperationLogFilter`，erp-gateway-* 接入）
- [ ] **T18** [P2] MQ 广播刷缓存（`PermissionChangedEventListener`，秒级同步）

---

## Task 1: 创建 `erp_log` 库与 `operation_log` 表

**Priority:** P0
**Depends on:** 无
**Files:**
- Create: `sql/erp_log/V1_0_0__create_database.sql`
- Create: `sql/erp_log/V1_0_0__create_operation_log.sql`

**任务描述：**

按设计文档 §7 创建 `erp_log` 数据库与 `operation_log` 表，含全部字段和 6 个索引（`idx_user_time`、`idx_permission`、`idx_status_time`、`idx_trace`、`idx_occurred`、`idx_service_time`）。

**验收标准：**
- [ ] SQL 在干净的 MySQL 8.0 实例上执行成功，零报错
- [ ] `SHOW INDEX FROM operation_log` 显示 6 个索引齐全
- [ ] `INSERT` 一条测试数据正常写入，所有字段类型与设计文档一致
- [ ] 字符集为 `utf8mb4` / `utf8mb4_unicode_ci`
- [ ] 文件命名遵循项目 Flyway 约定（参考现有 `sql/` 目录）

---

## Task 2: 创建 `erp-common-log` 模块依赖与自动装配骨架

**Priority:** P0
**Depends on:** 无
**Files:**
- Modify: `erp-commons/erp-common-log/pom.xml`
- Create: `erp-commons/erp-common-log/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（如未存在）
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/config/OperationLogProperties.java`

**任务描述：**

补全 `erp-common-log` 模块的依赖：`erp-common-auth`（Sa-Token 注解）、`erp-common-redis`（L2 缓存）、`erp-common-mybatis`（dynamic-datasource）、`spring-boot-starter-aop`、`com.github.ben-manes.caffeine:caffeine`、`spring-boot-starter-validation`。创建 `OperationLogProperties`（`@ConfigurationProperties("erp.operation-log")`），声明 `enabled` / `permission-resolver.*` / `async.*` / `retain-days` 等配置项。

**验收标准：**
- [ ] `mvn -pl erp-commons/erp-common-log -am compile` 编译通过
- [ ] `OperationLogProperties` 字段与设计文档 §5.4 一致，含默认值
- [ ] `AutoConfiguration.imports` 文件存在（先放 placeholder，后续 Task 9 填充）
- [ ] 无循环依赖（`mvn dependency:tree` 检查）

---

## Task 3: 实现注解契约

**Priority:** P0
**Depends on:** T02
**Files:**
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/annotation/LogParam.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/annotation/LogResult.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/annotation/LogIgnore.java`
- Test: `erp-commons/erp-common-log/src/test/java/com/erp/common/log/annotation/AnnotationContractTest.java`

**任务描述：**

定义三个注解。`@LogParam` 同时支持方法级（记所有参数）和参数级（记单个参数），含可选 `value()` 字段名覆盖；`@LogResult` 仅方法级；`@LogIgnore` 仅方法级。

**验收标准：**
- [ ] 三个注解的 `@Target` / `@Retention(RUNTIME)` / `@Documented` 正确
- [ ] `@LogParam` 同时支持 `METHOD` 和 `PARAMETER` target
- [ ] 单元测试用反射验证注解可被正确读取
- [ ] Javadoc 描述用法（含示例代码）

---

## Task 4: 实现日志实体与 Mapper

**Priority:** P0
**Depends on:** T01, T02
**Files:**
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/entity/OperationLog.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/mapper/OperationLogMapper.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/enums/OperationStatus.java`

**任务描述：**

`OperationLog` 实体含 §7.2 所有字段，使用 `@TableName("operation_log")` 与 MyBatis-Plus 注解；`OperationLogMapper extends BaseMapper<OperationLog>` 并加 `@DS("log")`；新增 `deleteBefore(threshold, batchSize)` 方法用于清理任务。`OperationStatus` 枚举：`SUCCESS / FAILURE / DENIED / UNAUTHORIZED`。

**验收标准：**
- [ ] 实体字段命名与 SQL 列对齐（snake_case ↔ camelCase 由 MyBatis-Plus 默认映射）
- [ ] `@DS("log")` 注解在 Mapper 类上，确保数据源切换
- [ ] `deleteBefore` 使用 `LIMIT` 分批删除，`@Param` 注解齐全
- [ ] 无 createdBy / updatedBy 字段（日志不审计自己）

---

## Task 5: 实现敏感字段脱敏器

**Priority:** P0
**Depends on:** T02
**Files:**
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/masker/SensitiveMasker.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/masker/SensitiveFieldRegistry.java`
- Test: `erp-commons/erp-common-log/src/test/java/com/erp/common/log/masker/SensitiveMaskerTest.java`

**任务描述：**

`SensitiveFieldRegistry` 内置硬编码黑名单（`password / pwd / secret / token / idCard / bankCard / cvv` 等），不区分大小写。`SensitiveMasker` 接受 `Map<String, Object>` 或 POJO，递归替换匹配字段为 `***`，支持嵌套对象、数组、Map 三种结构。

**验收标准：**
- [ ] 单元测试覆盖：扁平字段、嵌套对象、List 元素、Map value、大小写不敏感
- [ ] `Password`、`PASSWD`、`pwd` 都能被匹配
- [ ] 非敏感字段（如 `username`）保持原值
- [ ] 性能：1000 条记录脱敏耗时 < 100ms（基准测试）

---

## Task 6: 实现两级缓存权限解析器

**Priority:** P0
**Depends on:** T02
**Files:**
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/resolver/PermissionMeta.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/resolver/PermissionNameResolver.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/resolver/CaffeineRedisPermissionResolver.java`
- Test: `erp-commons/erp-common-log/src/test/java/com/erp/common/log/resolver/CaffeineRedisPermissionResolverTest.java`

**任务描述：**

按设计文档 §3 / §5 实现两级缓存。`PermissionMeta` POJO 含 `code/name/module/type`；`PermissionNameResolver` 接口；`CaffeineRedisPermissionResolver` 默认实现：L1 Caffeine 容量 10000、TTL 10 分钟、`Optional` 防穿透；L2 Redisson `RMap<String,String>` 操作 `erp:permission:all`，硬性 200ms 超时；`@PostConstruct` 启动预热（3 秒超时不阻塞应用启动）；L3 Feign 兜底默认关闭（仅声明扩展点）。

**验收标准：**
- [ ] L1 命中：单元测试验证 Caffeine 命中后不查 Redis
- [ ] L1 miss → L2 命中：mock Redisson 返回 JSON，验证写回 L1
- [ ] L2 超时：mock 200ms 不返回，resolver 返回 null
- [ ] 缓存穿透防御：连续查询不存在的 code，Redis 只调用一次
- [ ] 启动预热失败不阻止 Bean 创建（验证 `@PostConstruct` 异常被吞掉）
- [ ] `recordStats=true`，可被 micrometer 自动绑定指标

---

## Task 7: 实现异步写入器

**Priority:** P0
**Depends on:** T02, T04, T05
**Files:**
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/writer/LogContext.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/writer/AsyncLogWriter.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/writer/LogEntityBuilder.java`
- Test: `erp-commons/erp-common-log/src/test/java/com/erp/common/log/writer/AsyncLogWriterTest.java`

**任务描述：**

`LogContext` 是切面构造的中间载体（含全部上下文 + 参数引用 + 是否记全参标志）。`AsyncLogWriter` 内置 `ThreadPoolExecutor`（core=4, max=16, keepalive=60s, queue=ArrayBlockingQueue(1000), DiscardOldestPolicy, threadName="op-log-%d"）；`submit(ctx)` 方法异步执行：序列化 → `SensitiveMasker` 脱敏 → `LogEntityBuilder.build()` → `mapper.insert()`，全程 try-catch 不抛。`LogEntityBuilder` 负责把 LogContext 转成 OperationLog 实体（含异常消息截断到 2000 字符）。

**验收标准：**
- [ ] 队列满时 `DiscardOldestPolicy` 工作（单元测试灌满 1001 条，验证只剩 1000）
- [ ] DB 写入失败不抛异常（mock mapper.insert 抛异常，验证 submit 不抛）
- [ ] 异常消息超过 2000 字符被截断（用 `StringUtils.abbreviate`）
- [ ] 优雅停机：`@PreDestroy` 调用 `pool.shutdown()` 等待 5 秒
- [ ] 线程名称包含 `op-log-` 前缀（线程 dump 容易定位）

---

## Task 8: 实现切面

**Priority:** P0
**Depends on:** T03, T05, T06, T07
**Files:**
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/aspect/OperationLogAspect.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/aspect/ParamCollector.java`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/aspect/HttpServletRequestSupplier.java`
- Test: `erp-commons/erp-common-log/src/test/java/com/erp/common/log/aspect/OperationLogAspectTest.java`

**任务描述：**

实现核心切面（设计文档 §6）。`@Order(Ordered.HIGHEST_PRECEDENCE)` 保证比 Sa-Token 切面更外层。切点：`@annotation(SaCheckPermission) && !@annotation(LogIgnore)`。`@Around` 主流程：构建 LogContext → 解析权限名 → 收集入参 → `pjp.proceed()` → 捕获 `NotPermissionException`（DENIED）/ `NotLoginException`（UNAUTHORIZED）/ 其他 Throwable（FAILURE，强制记全参）→ finally 中计算 duration、提交异步写入。`ParamCollector` 按 §6.6 规则收集（跳过 HttpServletRequest/Response/MultipartFile/InputStream/Resource）。`HttpServletRequestSupplier` 通过 `RequestContextHolder` 取请求，可能 null（@Async 调用时）。

**验收标准：**
- [ ] 9 个核心单元测试用例全部通过（参考设计文档 §9.2.1）
- [ ] 切面任何环节抛异常都不影响业务（mock resolver 抛 NPE，业务方法返回正常）
- [ ] 鉴权失败时 status=DENIED，且原异常仍向上抛（被 Sa-Token 全局处理器接住）
- [ ] 业务异常时 `request_params` 含全部入参（异常时记全参）
- [ ] `@LogIgnore` 标记的方法切面不进入

---

## Task 9: 实现自动配置

**Priority:** P0
**Depends on:** T06, T07, T08
**Files:**
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/config/OperationLogAutoConfiguration.java`
- Modify: `erp-commons/erp-common-log/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**任务描述：**

`@AutoConfiguration` 装配 `OperationLogAspect` / `PermissionNameResolver` / `AsyncLogWriter` / `SensitiveMasker` / `HttpServletRequestSupplier` 等 Bean。所有 Bean 通过 `@ConditionalOnMissingBean` 允许业务方覆盖。整体类加 `@ConditionalOnProperty(prefix = "erp.operation-log", name = "enabled", havingValue = "true")`，**默认关闭**（设计文档 §7.5.2 约定）。

**验收标准：**
- [ ] `enabled=false` 时所有 Bean 都不创建（`@SpringBootTest` 验证 `applicationContext.getBean()` 抛异常）
- [ ] `enabled=true` 时 5 个 Bean 都被创建
- [ ] `@MapperScan` 把 `OperationLogMapper` 纳入扫描（或显式 `@Mapper` 注解）
- [ ] `AutoConfiguration.imports` 文件包含本类全限定名

---

## Task 10: erp-system 维护 Redis Hash

**Priority:** P0
**Depends on:** T01
**Files:**
- Create: `erp-services/erp-system/src/main/java/com/erp/system/permission/PermissionRedisSyncService.java`
- Modify: erp-system 中权限 CRUD Service（位置由开发自行 grep 定位 `sys_permission` 相关 Service）
- Test: `erp-services/erp-system/src/test/java/com/erp/system/permission/PermissionRedisSyncServiceTest.java`

**任务描述：**

`PermissionRedisSyncService` 提供 4 个公开方法：`syncAll()` / `syncOne(code)` / `removeOne(code)` / `removeAll()`。`@PostConstruct` 监听 `ApplicationReadyEvent`（不要在 `@PostConstruct` 直接调，避免阻塞启动）；启动后异步执行一次 `syncAll()` 全量同步。erp-system 现有的权限新增/修改/删除 Service 在 DB 事务提交后调用对应同步方法（建议用 `TransactionSynchronizationManager.registerSynchronization` 的 `afterCommit` 钩子）。Redis 失败仅记 ERROR，不回滚 DB。

**验收标准：**
- [ ] 集成测试：启动 erp-system，验证 `redisson.getMap("erp:permission:all").size()` 等于 `sys_permission` 表行数
- [ ] 新增权限：DB 提交后 Redis Hash 多一条 field
- [ ] 删除权限：DB 提交后 Redis Hash 少一条 field
- [ ] Redis 不可用时：DB 操作仍然成功，logback 输出 ERROR
- [ ] 启动同步异步化（不阻塞 erp-system 启动 > 1s）

---

## Task 11: 集成测试

**Priority:** P0
**Depends on:** T09
**Files:**
- Create: `erp-commons/erp-common-log/src/test/java/com/erp/common/log/integration/OperationLogIntegrationTest.java`
- Create: `erp-commons/erp-common-log/src/test/resources/sql/erp_log_init.sql`

**任务描述：**

按设计文档 §9.3 用 Testcontainers + `@SpringBootTest` 编写端到端测试。Testcontainers 启动 MySQL（init script 加载 `operation_log` 表）+ Redis（带密码）。3 个核心场景：
1. 调用带 `@SaCheckPermission` 的 mock Controller，等待异步队列完成，查 DB 验证记录
2. 调用无权限的接口（mock Sa-Token 抛 `NotPermissionException`），验证 status=DENIED
3. 调用业务异常接口，验证 status=FAILURE 且 `request_params` 包含全部入参

**验收标准：**
- [ ] 3 个测试用例全部通过
- [ ] 用 `Awaitility` 等待异步线程池完成（`atMost(5, SECONDS)`）
- [ ] 测试结束容器自动清理
- [ ] 测试在 CI 环境（无本地 MySQL）也能运行

---

## Task 12: 灰度先行：erp-system 接入

**Priority:** P0
**Depends on:** T09, T10, T11
**Files:**
- Modify: `erp-services/erp-system/pom.xml`（增加 `erp-common-log` 依赖）
- Modify: `erp-services/erp-system/src/main/resources/application.yml`（启用 + 配置 log 数据源）
- Modify: erp-system 启动类或扫描配置（如需要让 mapper 扫到）

**任务描述：**

erp-system 引入 `erp-common-log` 依赖；`application.yml` 中 `erp.operation-log.enabled=true`；`spring.datasource.dynamic.datasource.log` 添加 erp_log 库连接配置；启动服务，调用一个带 `@SaCheckPermission` 的接口（如 `/system/role/list`），验证日志被记录。观察 1~3 天的指标（业务延迟 P99、错误率、操作日志写入量）。

**验收标准：**
- [ ] 服务正常启动（无配置/Bean 冲突）
- [ ] 调用 `@SaCheckPermission` 接口后，erp_log.operation_log 出现对应记录
- [ ] 记录中 `permission_code`、`permission_name`、`user_id`、`user_name`、`status`、`duration_ms` 都有值
- [ ] 业务接口 P99 延迟相对上线前增长 < 1ms（用 actuator metrics 对比）
- [ ] Caffeine 命中率 > 95%（埋点或 actuator metrics）

---

## Task 13: 扩展到 erp-auth、erp-base

**Priority:** P0
**Depends on:** T12
**Files:**
- Modify: `erp-services/erp-auth/pom.xml` + `application.yml`
- Modify: `erp-services/erp-base/pom.xml` + `application.yml`

**任务描述：**

按 T12 同样的方式接入 erp-auth 和 erp-base，启用日志能力。观察 3~7 天，确认两级缓存在多服务环境下表现稳定（同一权限 code 在多个服务都能正确解析名字）。

**验收标准：**
- [ ] 两个服务都正常启动并写日志
- [ ] 同一个权限 code 在 erp-auth、erp-base、erp-system 三处的 `permission_name` 一致
- [ ] erp-system 重启后，erp-auth/erp-base 仍能正常记日志（验证 L2 Redis 解耦）
- [ ] erp_log.operation_log 表 `service_name` 字段正确区分三个服务

---

## Task 14: 全量推广到剩余 6 个业务服务

**Priority:** P0
**Depends on:** T13
**Files:**
- Modify: `erp-services/erp-inventory/pom.xml` + `application.yml`
- Modify: `erp-services/erp-purchase/pom.xml` + `application.yml`
- Modify: `erp-services/erp-sale/pom.xml` + `application.yml`
- Modify: `erp-services/erp-finance/pom.xml` + `application.yml`
- Modify: `erp-services/erp-production/pom.xml` + `application.yml`
- Modify: `erp-services/erp-report/pom.xml` + `application.yml`

**任务描述：**

依次接入剩余 6 个服务。建议每接入 2 个观察一天，避免一次性放量。提前确认 erp_log MySQL 实例的 IO 容量足够（参考容量预估：9 服务总日产量约 1~2 万条）。

**验收标准：**
- [ ] 9 个服务都启用并能写日志
- [ ] erp_log MySQL 实例 IO/连接数无异常告警
- [ ] 任一服务故障不影响其他服务记日志（验证服务间独立）
- [ ] 异步队列水位长期 < 100（无堆积）

---

## Task 15: XXL-JOB 清理任务

**Priority:** P1
**Depends on:** T12
**Files:**
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/job/OperationLogCleanupJob.java`
- Modify: erp-system XXL-JOB 调度器配置（添加 Job 注册）

**任务描述：**

`@XxlJob("operationLogCleanupJob")` 方法：按 `retain-days` 配置（默认 90）计算 threshold，循环 `mapper.deleteBefore(threshold, 5000)` 直到删完，每批 sleep 100ms 让出锁。任务部署在 erp-system 服务上承载（最稳定的服务）。XXL-JOB 调度规则配置为每天凌晨 3 点执行一次。

**验收标准：**
- [ ] 手动触发任务，超过保留期的日志被删除
- [ ] 单批 5000 条，sleep 100ms 控制锁竞争
- [ ] 任务执行日志通过 `XxlJobHelper.log` 输出删除条数
- [ ] `retain-days` 可通过 nacos / 环境变量动态调整

---

## Task 16: Prometheus 指标接入

**Priority:** P1
**Depends on:** T07, T12
**Files:**
- Modify: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/writer/AsyncLogWriter.java`（注入 MeterRegistry）
- Modify: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/resolver/CaffeineRedisPermissionResolver.java`（绑定 Caffeine stats 到 MeterRegistry）

**任务描述：**

按设计文档 §8.2 暴露 7 项指标：`op_log_queue_size` / `op_log_submitted_total` / `op_log_inserted_total` / `op_log_dropped_total{reason}` / `op_log_aspect_duration_seconds` / `op_log_db_insert_seconds` / Caffeine 命中率。Caffeine 通过 `CaffeineCacheMetrics.monitor(meterRegistry, cache, "permission_l1")` 自动绑定。

**验收标准：**
- [ ] `/actuator/prometheus` 端点能看到 7 项指标
- [ ] 故意灌满队列，`op_log_dropped_total{reason="queue_full"}` 计数增长
- [ ] 指标名称与设计文档一致（便于后续 Grafana 面板配置）

---

## Task 17: Reactive 网关日志（可选）

**Priority:** P2
**Depends on:** T14
**Files:**
- Create: `erp-gateway/erp-gateway-common/src/main/java/com/erp/gateway/common/filter/ReactiveOperationLogFilter.java`
- Modify: erp-gateway-pc / erp-gateway-app / erp-gateway-open 的 starter 配置

**任务描述：**

WebFlux 不支持 `@Aspect`，需要在网关层用 GlobalFilter 实现。复用 §6 的 `PermissionNameResolver` 和 §7 的 `AsyncLogWriter`，但上下文从 `ServerWebExchange` 提取。仅在网关有"接入日志"诉求时实施。

**验收标准：**
- [ ] 网关入口请求被记录到 erp_log.operation_log
- [ ] 业务服务的 AOP 切面与网关 Filter 不重复记录（用 `service_name` 区分）
- [ ] 网关延迟增加 < 1ms

---

## Task 18: MQ 广播刷缓存（可选）

**Priority:** P2
**Depends on:** T10, T14
**Files:**
- Create: `erp-services/erp-system/src/main/java/com/erp/system/permission/event/PermissionChangedEvent.java`
- Modify: `erp-services/erp-system/src/main/java/com/erp/system/permission/PermissionRedisSyncService.java`（同步后发送 MQ）
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/listener/PermissionChangedEventListener.java`

**任务描述：**

复用项目已有的 `EventPublisher` / `BaseEvent`（提交 c8930c1 铺垫），erp-system 在 Redis Hash 同步后通过 RabbitMQ 广播 `PermissionChangedEvent`。erp-common-log 中 `PermissionChangedEventListener`（`@RabbitListener`）监听后清掉本地 Caffeine 对应条目，下次访问时从 L2 重新加载。仅在业务有"权限改名要秒级生效"诉求时实施。

**验收标准：**
- [ ] erp-system 改一条权限名，3 秒内所有 9 个服务的 Caffeine 条目都失效
- [ ] MQ 不可用时降级到 TTL（10 分钟）兜底，业务不受影响
- [ ] Listener 异常不影响其他业务消费

---

## 执行约定

- **每个任务独立 PR**：commit message 格式 `feat(common-log): T{编号} {任务名}`
- **TDD 优先**：测试先于实现，单元测试与代码同 PR
- **按依赖关系顺序执行**：上一个任务的验收标准全部满足再开始下一个
- **观察期严格遵守**：T12 / T13 / T14 的灰度观察期不能压缩，避免一次性放量出事故
- **回退预案**：每个任务都应能通过 `enabled: false` 一键关闭，不需要回滚代码

## 设计文档对应关系

| 任务 | 对应设计文档章节 |
|------|---|
| T01 | §7 表结构与存储 |
| T02 | §3 整体架构、§5.4 配置项 |
| T03 | §4 注解契约 |
| T04 | §7.4 实体类 |
| T05 | §4.3 自动黑名单 |
| T06 | §5 权限名两级缓存 |
| T07 | §6.4 异步写入器 |
| T08 | §6 AOP 切面流程 |
| T09 | §6.10 关闭整个能力的逃生通道 |
| T10 | §5.2 写入侧 |
| T11 | §9.3 集成测试 |
| T12-T14 | §9.5 上线策略 |
| T15 | §6.11 XXL-JOB 清理 |
| T16 | §8.2 Prometheus 指标 |
| T17 | §4.8 Reactive 网关侧 |
| T18 | §10 后续演进 / §3 部署视图 MQ 广播 |
