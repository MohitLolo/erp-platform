# ERP 微服务项目架构重构设计文档

**日期：** 2026-04-28  
**状态：** 已确认，待实施  

---

## 一、背景

项目已完成第一阶段基础代码生成（网关/认证/系统/业务服务/SQL/K8s配置等）。本次重构针对以下 6 个架构改进点，目标是提升解耦性、可维护性，并为后续业务扩展打下基础。

---

## 二、已确认技术决策

### 决策 1：文档生成方案 → smart-doc

- **选择：** smart-doc（基于 Javadoc 注释，编译期生成）
- **原因：** 零侵入（无需 @Api 注解），生成 HTML/Markdown/OpenAPI，支持 Torna 推送，CI 友好
- **落地：** 在 `erp-services` 父 pom 统一声明 `smart-doc-maven-plugin`，各服务 `src/main/resources/smart-doc.json` 配置输出路径

### 决策 2：分布式能力 → Redisson

- **选择：** Redisson（基于 Redis 的分布式工具库）
- **封装位置：** `erp-common-redis` 子模块
- **提供能力：**
  - `RedissonLockHelper`（可重入锁 RLock）
  - `RedissonRateLimiterHelper`（RRateLimiter）
  - `RedissonBloomFilterHelper`（RBloomFilter）
  - `RedissonDelayedQueueHelper`（RDelayedQueue）
- **依赖：** `redisson-spring-boot-starter 3.27.x`

### 决策 3：熔断限流 → 双轨制

- **选择：** Resilience4j（服务间 Feign 熔断） + Sentinel（网关层动态流控）
- **原因：** 两者定位不同，Resilience4j 轻量适合客户端；Sentinel 控制台适合网关动态规则
- **落地：** Resilience4j 保持现有配置；Gateway 引入 Sentinel Gateway，Dashboard 使用 sentinel-dashboard:2.0.0-alpha

### 决策 4：项目结构 → 三层父模块（Plan B）

```
erp-platform/                    ← 根 pom（版本仲裁，不含业务代码）
├── erp-commons/                  ← 公共模块父 pom（独立发布到 Maven 私服）
│   ├── erp-common-core/          ← 统一响应/异常/常量/TTL上下文
│   ├── erp-common-log/           ← MDC + SkyWalking TraceId 日志
│   ├── erp-common-auth/          ← Sa-Token JWT 工具/鉴权注解
│   ├── erp-common-redis/         ← Redis + Redisson 封装
│   ├── erp-common-mybatis/       ← MyBatis-Plus 配置/自动填充/多租户插件
│   ├── erp-common-mq/            ← RabbitMQ 事件基类/发布工具
│   └── erp-common-web/           ← GlobalExceptionHandler/WebMvcConfig
├── erp-apis/                     ← 服务间 API 父 pom（独立版本发布到私服）
│   ├── erp-api-system/           ← SystemUserFeign + DTO
│   ├── erp-api-base/             ← MaterialFeign/WarehouseFeign + DTO
│   ├── erp-api-inventory/        ← StockFeign + DTO
│   └── erp-api-finance/          ← ReceivableFeign + DTO
└── erp-services/                 ← 业务服务父 pom（不发布）
    ├── erp-gateway/
    ├── erp-auth/
    ├── erp-system/
    ├── erp-base/
    ├── erp-sale/
    ├── erp-purchase/
    ├── erp-inventory/
    ├── erp-finance/
    ├── erp-production/
    └── erp-report/
```

**发布策略：**
- `erp-commons/*`：groupId=`com.erp.commons`，独立 version，CI 打 tag 触发发布
- `erp-apis/*`：groupId=`com.erp.api`，独立 version，跟随 API 接口变更发布
- `erp-services/*`：仅打 Docker 镜像，不发布 jar

### 决策 5：服务间 API 模块 → Option B（独立版本发布到私服）

- **选择：** erp-apis 下各 api 模块独立 pom，有自己的 version，发布到 Maven 私服（Harbor 内嵌 Maven Registry 或 Nexus）
- **调用方：** 在 pom.xml 中声明 `erp-api-inventory:1.0.0` 依赖，而非直接依赖 erp-inventory 服务
- **接口范围：** 仅包含 Feign 接口、请求 DTO、响应 DTO、枚举常量；不含业务逻辑

### 决策 6：线程上下文传递 → TransmittableThreadLocal (TTL)

- **选择：** 阿里 `transmittable-thread-local 2.14.5`
- **替换位置：**
  - `TenantContextHolder`（erp-common-core）
  - `UserContextHolder`（erp-common-core）
- **线程池配置：** 所有业务线程池使用 `TtlExecutors.getTtlExecutorService()` 包装
- **Feign 传递：** 自定义 `TtlFeignRequestInterceptor` 在请求头中携带 tenantId + userId

---

## 三、数据库范围（简化版）

**本次仅实现认证/授权/多租户相关表：**

```
erp_system 库：
├── undo_log          ← Seata
├── sys_tenant        ← 租户表
├── sys_user          ← 用户表（含 tenant_id）
├── sys_role          ← 角色表
├── sys_permission    ← 权限表（菜单+按钮+API）
├── sys_user_role     ← 用户角色关联
├── sys_role_permission ← 角色权限关联
└── sys_dept          ← 部门表

seata 库：
├── global_table
├── branch_table
├── lock_table
└── distributed_lock
```

业务域（采购/销售/库存/财务/生产）的表结构待业务需求确定后再补充。

---

## 四、架构约束

1. `erp-commons` 模块间不允许互相依赖（单向依赖链：core ← 其他）
2. `erp-apis` 模块只依赖 `erp-common-core`，不依赖任何业务服务
3. 业务服务只允许依赖 `erp-commons/*` 和 `erp-apis/*`，不允许依赖其他服务的源码
4. TTL 上下文在 Gateway 入口填充，在各服务 Filter 中恢复，在响应后清理
5. smart-doc 注释规范：所有 Controller 方法必须有 `@param` 和 `@return` Javadoc

---

## 五、实施顺序（详见实施计划）

1. 根 pom 重构（版本仲裁）
2. erp-commons 公共模块搭建（core → log → auth → redis → mybatis → mq → web）
3. erp-apis API 模块搭建
4. erp-services 父模块 + 各业务服务 pom 迁移
5. TTL 替换（TenantContextHolder / UserContextHolder）
6. Redisson 集成（erp-common-redis）
7. smart-doc 配置
8. SQL 精简（只保留 auth/rbac/tenant）
9. 验证编译通过

