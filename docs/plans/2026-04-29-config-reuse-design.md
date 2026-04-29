# ERP 微服务配置复用设计文档

**日期：** 2026-04-29  
**状态：** 已确认，待实施  
**背景：** 项目从 Nacos 配置中心迁移至 K8s 原生 ConfigMap，10 个微服务存在大量重复配置，需要统一抽离复用机制。

---

## 1. 问题背景

### 当前痛点

- 10 个业务服务的 `application.yml` 存在大量完全相同的配置块
- 重复内容包括：mybatis-plus 全局设置、sa-token token 参数、actuator 暴露端点、logging pattern、feign 超时、RabbitMQ listener、Redis 连接池
- `token-style` 不一致（部分服务 `terse-uuid`，部分 `jwt-default`）
- `server.shutdown: graceful` 在 auth/system/sale/report 四个服务中缺失
- 数据库连接池参数散落各服务，无统一基线

### Nacos vs K8s ConfigMap 模型差异

| 特性 | Nacos shared-configs | K8s ConfigMap |
|------|---------------------|---------------|
| 加载方式 | 运行时从服务端拉取 | 挂载为文件或环境变量 |
| 环境隔离 | Namespace 隔离 | K8s Namespace 隔离 |
| 热更新 | 支持实时推送 | 需重启 Pod（或配合 Reloader） |
| 本地开发 | 需连接 Nacos 服务 | `optional:` 前缀可跳过 |
| 版本管理 | Nacos 内部历史版本 | GitOps，随代码仓库版本化 |

---

## 2. 设计目标

1. **开发友好**：开发人员本地 `java -jar` 零额外准备，无需挂载任何文件
2. **运维轻简**：不引入额外配置服务（无 Spring Cloud Config Server、无 Nacos）
3. **按需引入**：服务按需选择引入哪些共享配置文件，不需要的模块不引入
4. **数据库连接池服务自治**：HikariCP 参数由各服务根据业务情况自行维护
5. **敏感信息隔离**：密码、密钥只走 K8s Secret → 环境变量，永不进配置文件

---

## 3. 整体分层架构

配置按**变化频率**分三层，优先级递增（高层覆盖低层）：

```
优先级（低 → 高）
┌─────────────────────────────────────────────────────────────┐
│  Layer 1：JAR 静态默认层（erp-commons 各模块内嵌）           │
│  载体：classpath:erp-defaults/*.yml + @AutoConfiguration    │
│  内容：mybatis 全局、server.shutdown、logging、feign 超时、  │
│        RabbitMQ listener 策略、Redis lettuce 连接池         │
│  变更成本：重新打 JAR + 全量部署（适合架构级固定参数）        │
├─────────────────────────────────────────────────────────────┤
│  Layer 2：K8s ConfigMap 环境共享层                           │
│  载体：/config/*.yml（volume mount，按职责拆多个文件）        │
│  内容：Redis/MQ 连接地址、sa-token 认证策略                  │
│  变更成本：改 ConfigMap 重启 Pod                             │
│  环境隔离：dev/test/prod namespace 各自维护同名 ConfigMap    │
├─────────────────────────────────────────────────────────────┤
│  Layer 3：服务专属层（各服务 application.yml）               │
│  载体：代码仓库各服务自己的 yml                              │
│  内容：app.name、port、DB url、HikariCP 连接池、seata group、│
│        type-aliases-package、spring.config.import 声明      │
└─────────────────────────────────────────────────────────────┘
                              ↑ 同时
┌─────────────────────────────────────────────────────────────┐
│  K8s Secret → 环境变量（敏感数据，不进任何 yml 文件）        │
│  DB_PASSWORD / REDIS_PASSWORD / SA_TOKEN_JWT_SECRET /       │
│  RABBITMQ_PASSWORD                                          │
│  服务 yml 中只写占位符：${DB_PASSWORD:本地开发默认值}         │
└─────────────────────────────────────────────────────────────┘
```

### 配置归属速查表

| 配置项 | 归属层 | 理由 |
|--------|--------|------|
| mybatis-plus logic-delete、下划线转驼峰、id-type | Layer 1 JAR | 架构决策，永不变 |
| server.shutdown: graceful | Layer 1 JAR | 生产基线，全服务统一 |
| logging pattern（含 SW_CTX） | Layer 1 JAR | 链路追踪格式，统一 |
| feign connect/read timeout | Layer 1 JAR | 通用默认值，服务可覆盖 |
| RabbitMQ listener ack-mode=manual、retry | Layer 1 JAR | 可靠消费架构决策 |
| Redis lettuce pool 参数 | Layer 1 JAR | 通用兜底，服务可覆盖 |
| sa-token token-style: jwt-default | Layer 1 JAR | 架构决策，全局统一 |
| Redis/MQ host、port | Layer 2 ConfigMap | 按环境不同 |
| sa-token timeout、active-timeout、is-concurrent | Layer 2 ConfigMap | 认证策略可按环境调 |
| **HikariCP maximum-pool-size / minimum-idle** | **Layer 3 服务专属** | **业务驱动，各服务自评估** |
| DB url、datasource driver | Layer 3 服务专属 | 每服务唯一 |
| seata tx-service-group | Layer 3 服务专属 | 每服务唯一 |
| type-aliases-package | Layer 3 服务专属 | 每服务唯一 |
| DB_PASSWORD、REDIS_PASSWORD、jwt-secret-key | K8s Secret → 环境变量 | 敏感信息 |

---

## 4. Layer 1：JAR 内嵌默认值实现方案

### 4.1 技术机制

利用 Spring Boot 3 的 `AutoConfiguration` + `@PropertySource` + `YamlPropertySourceFactory`，以**最低优先级**注入默认值。服务无需手动声明，引入 Maven 依赖即自动生效。

`@PropertySource` 天然是 Spring Boot 最低优先级，服务 `application.yml` 中任何值都会自动覆盖 JAR 默认值，**完全无侵入**。

### 4.2 公共工具类

放在 `erp-common-core` 中，供所有模块复用：

```java
// com.erp.common.core.config.YamlPropertySourceFactory
public class YamlPropertySourceFactory implements PropertySourceFactory {
    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource)
            throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource.getResource());
        Properties properties = factory.getObject();
        return new PropertiesPropertySource(
            resource.getResource().getFilename(), properties);
    }
}
```

### 4.3 各模块 AutoConfiguration 类

每个需要提供默认配置的 `erp-common-*` 模块，在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册：

```
# erp-common-mybatis 模块
com.erp.common.mybatis.config.MybatisDefaultsAutoConfiguration

# erp-common-web 模块
com.erp.common.web.config.WebDefaultsAutoConfiguration

# erp-common-mq 模块
com.erp.common.mq.config.RabbitmqDefaultsAutoConfiguration

# erp-common-redis 模块
com.erp.common.redis.config.RedisDefaultsAutoConfiguration
```

### 4.4 各模块默认 YAML 文件

**`erp-common-mybatis/src/main/resources/erp-defaults/mybatis-defaults.yml`**
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    default-enum-type-handler: com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
      id-type: assign_id
```

**`erp-common-web/src/main/resources/erp-defaults/web-defaults.yml`**
```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 5000
      circuitbreaker:
        enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{SW_CTX}] %logger{36} - %msg%n"
```

**`erp-common-mq/src/main/resources/erp-defaults/rabbitmq-defaults.yml`**
```yaml
spring:
  rabbitmq:
    virtual-host: /
    publisher-confirms: true
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000ms
```

**`erp-common-redis/src/main/resources/erp-defaults/redis-defaults.yml`**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 2
          max-wait: 1000ms
```

---

## 5. Layer 2：K8s ConfigMap 多文件设计

### 5.1 ConfigMap 拆分原则

按**变更频率 + 职责**拆分为多个独立文件，不合并成一个大文件：

| ConfigMap 名称 | 内容 | 挂载路径 |
|----------------|------|---------|
| `erp-config-infra` | Redis/MQ 连接地址、端口 | `/config/infra.yml` |
| `erp-config-auth` | Sa-Token 认证策略（超时、并发等） | `/config/auth.yml` |
| `erp-config-seata` | Seata 注册/配置类型（按需挂载） | `/config/seata.yml` |

### 5.2 ConfigMap 内容示例（dev namespace）

**`erp-config-infra`**
```yaml
spring:
  data:
    redis:
      host: redis-svc
      port: 6379
  rabbitmq:
    host: rabbitmq-svc
    port: 5672
    username: erp
```

**`erp-config-auth`**
```yaml
sa-token:
  token-name: Authorization
  token-style: jwt-default
  timeout: 86400
  active-timeout: 1800
  is-concurrent: false
  is-share: true
  is-read-header: true
  is-read-cookie: false
```

**`erp-config-seata`**
```yaml
seata:
  enabled: true
  registry:
    type: file
    file:
      name: registry.conf
  config:
    type: file
```

### 5.3 K8s Secret（敏感数据）

密码、密钥只通过 K8s Secret 注入为环境变量，永不写入 ConfigMap 文件：

```yaml
# K8s Secret 环境变量注入示例（Deployment spec）
env:
  - name: REDIS_PASSWORD
    valueFrom:
      secretKeyRef:
        name: erp-secrets
        key: redis-password
  - name: RABBITMQ_PASSWORD
    valueFrom:
      secretKeyRef:
        name: erp-secrets
        key: rabbitmq-password
  - name: SA_TOKEN_JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: erp-secrets
        key: jwt-secret
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: erp-secrets
        key: db-password
```

### 5.4 Pod Volume 挂载配置

```yaml
# Deployment spec 示例（erp-sale，需要 infra + auth）
volumeMounts:
  - name: config-infra
    mountPath: /config/infra.yml
    subPath: infra.yml
  - name: config-auth
    mountPath: /config/auth.yml
    subPath: auth.yml
volumes:
  - name: config-infra
    configMap:
      name: erp-config-infra
  - name: config-auth
    configMap:
      name: erp-config-auth
```

---

## 6. Layer 3：服务专属 application.yml 精简后结构

服务 `application.yml` 只保留**真正属于自己**的配置，通过 `spring.config.import` 按需声明引入：

```yaml
# erp-sale/src/main/resources/application.yml（精简后示例）
spring:
  application:
    name: erp-sale
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"   # Redis + MQ 连接串
      - "optional:file:/config/auth.yml"    # Sa-Token 策略
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_sale?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:                          # ← 连接池由服务自己维护
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

server:
  port: 8080

seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: erp-sale-group
  service:
    vgroup-mapping:
      erp-sale-group: default

mybatis-plus:
  type-aliases-package: com.erp.sale.domain.entity   # 服务专属
```

**各服务 `spring.config.import` 按需选择：**

| 服务 | infra.yml | auth.yml | seata.yml |
|------|-----------|----------|-----------|
| erp-gateway | ✅（Redis） | ✅ | ❌ |
| erp-auth | ✅ | ✅ | ❌ |
| erp-system | ✅ | ✅ | ❌ |
| erp-base | ✅ | ✅ | ✅ |
| erp-purchase | ✅ | ✅ | ✅ |
| erp-sale | ✅ | ✅ | ✅ |
| erp-inventory | ✅ | ✅ | ✅ |
| erp-finance | ✅ | ✅ | ✅ |
| erp-production | ✅ | ✅ | ✅ |
| erp-report | ✅ | ✅ | ❌ |

---

## 7. 本地开发体验

开发人员拿到代码后，**零额外准备**即可启动：

```bash
# 直接启动，无需任何文件挂载
java -jar erp-sale.jar

# 启动过程：
# 1. Layer 1 JAR 默认值生效（mybatis、logging、feign、rabbitmq listener）
# 2. /config/infra.yml 不存在 → optional: 静默跳过
# 3. Redis/MQ 地址用 application.yml 里的 localhost:xxx 默认值
# 4. 密码用占位符默认值（本地通常不设密码）
```

如需在本地模拟 K8s 环境（验证 ConfigMap 配置）：

```bash
# 方式一：手动创建文件
mkdir -p /config
cp deploy/configmap/dev/infra.yml /config/infra.yml
java -jar erp-sale.jar

# 方式二：环境变量直接覆盖（推荐）
REDIS_HOST=my-redis-server RABBITMQ_HOST=my-mq-server java -jar erp-sale.jar
```

---

## 8. 一致性修复清单

趁此次重构，顺便修复存量不一致问题：

| 问题 | 当前状态 | 修复方案 |
|------|---------|---------|
| `sa-token.token-style` | 5 服务 `terse-uuid` / 5 服务 `jwt-default` | 统一为 `jwt-default`，移入 `erp-config-auth` ConfigMap |
| `server.shutdown: graceful` | auth/system/sale/report 缺失 | 移入 Layer 1 `web-defaults.yml`，全服务自动获得 |
| Redis 端口硬编码 | 部分服务 `6379`，部分 `${REDIS_PORT:6379}` | 统一改为 `${REDIS_PORT:6379}` 或由 ConfigMap 提供 |
| 本地默认密码不一致 | `123456` / `Erp@123456` / `Erp@redis2024` 混用 | 统一为简单开发默认值，生产走 Secret |

---

## 9. 目录结构变更概览

```
erp-commons/
├── erp-common-core/
│   └── src/main/java/com/erp/common/core/config/
│       └── YamlPropertySourceFactory.java          ← 新增
├── erp-common-mybatis/
│   └── src/main/resources/
│       ├── erp-defaults/mybatis-defaults.yml        ← 新增
│       └── META-INF/spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← 新增
├── erp-common-web/
│   └── src/main/resources/
│       ├── erp-defaults/web-defaults.yml            ← 新增
│       └── META-INF/spring/AutoConfiguration.imports  ← 新增
├── erp-common-mq/
│   └── src/main/resources/
│       ├── erp-defaults/rabbitmq-defaults.yml       ← 新增
│       └── META-INF/spring/AutoConfiguration.imports  ← 新增
└── erp-common-redis/
    └── src/main/resources/
        ├── erp-defaults/redis-defaults.yml          ← 新增
        └── META-INF/spring/AutoConfiguration.imports  ← 新增

deploy/
└── configmap/
    ├── dev/
    │   ├── infra.yml                                ← 新增
    │   ├── auth.yml                                 ← 新增
    │   └── seata.yml                               ← 新增
    ├── test/
    │   ├── infra.yml
    │   ├── auth.yml
    │   └── seata.yml
    └── prod/
        ├── infra.yml
        ├── auth.yml
        └── seata.yml

erp-services/
└── erp-*/src/main/resources/application.yml        ← 精简，删除重复配置块，添加 spring.config.import
```
