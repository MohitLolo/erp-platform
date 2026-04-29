# K8s ConfigMap 三层配置复用实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 10 个微服务中重复的配置块抽离为三层复用结构（JAR 内嵌默认值 / K8s ConfigMap 环境文件 / 服务专属 yml），消除配置冗余并修复存量不一致问题。

**Architecture:** Layer 1 — erp-commons 各模块通过 `@AutoConfiguration` + `@PropertySource` 内嵌 YAML 默认值，以最低优先级注入；Layer 2 — `deploy/configmap/` 目录按环境维护共享配置文件，K8s volume 挂载到 `/config/*.yml`，服务通过 `spring.config.import: optional:file:/config/xxx.yml` 按需引入；Layer 3 — 各服务 `application.yml` 仅保留服务专属内容（DB url、HikariCP 连接池、seata group、type-aliases-package）。

**Tech Stack:** Spring Boot 3.2.5 / Java 17 / Maven 多模块 / K8s ConfigMap volume mount

---

## Task 1: 在 erp-common-core 中新增 YamlPropertySourceFactory

**Files:**
- Create: `erp-commons/erp-common-core/src/main/java/com/erp/common/core/config/YamlPropertySourceFactory.java`

**Steps:**

**Step 1: 创建工具类**

```java
package com.erp.common.core.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * 支持 @PropertySource 加载 YAML 文件的工厂类。
 * 用于各 erp-common-* 模块内嵌默认配置值（最低优先级，可被服务 yml 覆盖）。
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource)
            throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource.getResource());
        Properties properties = factory.getObject();
        String sourceName = (name != null && !name.isEmpty())
                ? name : resource.getResource().getFilename();
        return new PropertiesPropertySource(sourceName, properties);
    }
}
```

**Step 2: 验证文件存在**

```bash
ls erp-commons/erp-common-core/src/main/java/com/erp/common/core/config/
```
Expected: `YamlPropertySourceFactory.java`

---

## Task 2: erp-common-mybatis — 新增默认配置文件与 AutoConfiguration

**Files:**
- Create: `erp-commons/erp-common-mybatis/src/main/resources/erp-defaults/mybatis-defaults.yml`
- Create: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/config/MybatisDefaultsAutoConfiguration.java`
- Create: `erp-commons/erp-common-mybatis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Steps:**

**Step 1: 创建 YAML 默认值文件**

```yaml
# erp-commons/erp-common-mybatis/src/main/resources/erp-defaults/mybatis-defaults.yml
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

**Step 2: 创建 AutoConfiguration 类**

```java
package com.erp.common.mybatis.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 mybatis-plus 架构级默认配置（最低优先级）。
 * 各服务 application.yml 中的同名配置会自动覆盖此处默认值。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/mybatis-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class MybatisDefaultsAutoConfiguration {
    // 仅负责属性注入，Bean 定义保留在 MybatisPlusConfig
}
```

**Step 3: 注册到 Spring Boot AutoConfiguration**

文件内容（单行，无空格）：
```
com.erp.common.mybatis.config.MybatisDefaultsAutoConfiguration
```

注意：如果该文件已存在并有其他条目（如 MybatisPlusConfig），追加新行，不要覆盖已有条目。

**Step 4: 验证目录结构**

```bash
find erp-commons/erp-common-mybatis/src/main/resources -type f
```
Expected:
```
erp-commons/erp-common-mybatis/src/main/resources/erp-defaults/mybatis-defaults.yml
erp-commons/erp-common-mybatis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Task 3: erp-common-web — 新增默认配置文件与 AutoConfiguration

**Files:**
- Create: `erp-commons/erp-common-web/src/main/resources/erp-defaults/web-defaults.yml`
- Create: `erp-commons/erp-common-web/src/main/java/com/erp/common/web/config/WebDefaultsAutoConfiguration.java`
- Create: `erp-commons/erp-common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Steps:**

**Step 1: 创建 YAML 默认值文件**

```yaml
# erp-commons/erp-common-web/src/main/resources/erp-defaults/web-defaults.yml
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
      probes:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{SW_CTX}] %logger{36} - %msg%n"
```

**Step 2: 创建 AutoConfiguration 类**

```java
package com.erp.common.web.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 web 层架构级默认配置：优雅关闭、Feign 超时、Actuator、日志格式。
 * 最低优先级，各服务 application.yml 中的同名配置会自动覆盖。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/web-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class WebDefaultsAutoConfiguration {
}
```

**Step 3: 注册到 Spring Boot AutoConfiguration**

文件内容：
```
com.erp.common.web.config.WebDefaultsAutoConfiguration
```

---

## Task 4: erp-common-mq — 新增默认配置文件与 AutoConfiguration

**Files:**
- Create: `erp-commons/erp-common-mq/src/main/resources/erp-defaults/rabbitmq-defaults.yml`
- Create: `erp-commons/erp-common-mq/src/main/java/com/erp/common/mq/config/RabbitmqDefaultsAutoConfiguration.java`
- Create: `erp-commons/erp-common-mq/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Steps:**

**Step 1: 创建 YAML 默认值文件**

```yaml
# erp-commons/erp-common-mq/src/main/resources/erp-defaults/rabbitmq-defaults.yml
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

**Step 2: 创建 AutoConfiguration 类**

```java
package com.erp.common.mq.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 RabbitMQ 消费者架构级默认配置：手动ACK、重试策略。
 * 最低优先级，各服务 application.yml 中的同名配置会自动覆盖。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/rabbitmq-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class RabbitmqDefaultsAutoConfiguration {
}
```

**Step 3: 注册到 Spring Boot AutoConfiguration**

文件内容：
```
com.erp.common.mq.config.RabbitmqDefaultsAutoConfiguration
```

---

## Task 5: erp-common-redis — 新增默认配置文件与 AutoConfiguration

**Files:**
- Create: `erp-commons/erp-common-redis/src/main/resources/erp-defaults/redis-defaults.yml`
- Create: `erp-commons/erp-common-redis/src/main/java/com/erp/common/redis/config/RedisDefaultsAutoConfiguration.java`
- Create: `erp-commons/erp-common-redis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Steps:**

**Step 1: 创建 YAML 默认值文件**

```yaml
# erp-commons/erp-common-redis/src/main/resources/erp-defaults/redis-defaults.yml
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

**Step 2: 创建 AutoConfiguration 类**

```java
package com.erp.common.redis.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 Redis Lettuce 连接池默认配置。
 * 最低优先级，各服务 application.yml 中的同名配置会自动覆盖（如 erp-inventory 有自定义池大小）。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/redis-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class RedisDefaultsAutoConfiguration {
}
```

**Step 3: 注册到 Spring Boot AutoConfiguration**

文件内容：
```
com.erp.common.redis.config.RedisDefaultsAutoConfiguration
```

---

## Task 6: 创建 deploy/configmap/ 目录结构（三个环境）

**Files:**
- Create: `deploy/configmap/dev/infra.yml`
- Create: `deploy/configmap/dev/auth.yml`
- Create: `deploy/configmap/dev/seata.yml`
- Create: `deploy/configmap/test/infra.yml`
- Create: `deploy/configmap/test/auth.yml`
- Create: `deploy/configmap/test/seata.yml`
- Create: `deploy/configmap/prod/infra.yml`
- Create: `deploy/configmap/prod/auth.yml`
- Create: `deploy/configmap/prod/seata.yml`

**Steps:**

**Step 1: 创建 dev 环境 infra.yml**

```yaml
# deploy/configmap/dev/infra.yml
# K8s ConfigMap: erp-config-infra (dev namespace)
# 挂载路径: /config/infra.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 3000ms
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:erp}
    password: ${RABBITMQ_PASSWORD:}
```

**Step 2: 创建 dev 环境 auth.yml**

```yaml
# deploy/configmap/dev/auth.yml
# K8s ConfigMap: erp-config-auth (dev namespace)
# 挂载路径: /config/auth.yml
sa-token:
  token-name: Authorization
  token-style: jwt-default
  timeout: 86400
  active-timeout: 1800
  is-concurrent: false
  is-share: true
  is-read-header: true
  is-read-cookie: false
  jwt-secret-key: ${SA_TOKEN_JWT_SECRET:erp-platform-secret-key-2024}
```

**Step 3: 创建 dev 环境 seata.yml**

```yaml
# deploy/configmap/dev/seata.yml
# K8s ConfigMap: erp-config-seata (dev namespace)
# 挂载路径: /config/seata.yml
# 注意: tx-service-group 和 application-id 保留在各服务 application.yml 中
seata:
  enabled: true
  registry:
    type: file
    file:
      name: registry.conf
  config:
    type: file
```

**Step 4: 创建 test 环境（从 dev 复制后修改连接地址）**

`deploy/configmap/test/infra.yml`：
```yaml
# deploy/configmap/test/infra.yml
# K8s ConfigMap: erp-config-infra (test namespace)
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis-svc}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 3000ms
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq-svc}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:erp}
    password: ${RABBITMQ_PASSWORD:}
```

`deploy/configmap/test/auth.yml` — 与 dev 内容相同（超时策略不变），直接复用 dev 内容。

`deploy/configmap/test/seata.yml` — 与 dev 内容相同，直接复用。

**Step 5: 创建 prod 环境**

`deploy/configmap/prod/infra.yml`：
```yaml
# deploy/configmap/prod/infra.yml
# K8s ConfigMap: erp-config-infra (prod namespace)
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis-svc}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 3000ms
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq-svc}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:erp}
    password: ${RABBITMQ_PASSWORD:}
```

`deploy/configmap/prod/auth.yml`：
```yaml
# deploy/configmap/prod/auth.yml
# K8s ConfigMap: erp-config-auth (prod namespace)
sa-token:
  token-name: Authorization
  token-style: jwt-default
  timeout: 43200
  active-timeout: 1800
  is-concurrent: false
  is-share: true
  is-read-header: true
  is-read-cookie: false
  jwt-secret-key: ${SA_TOKEN_JWT_SECRET:}
```

`deploy/configmap/prod/seata.yml` — 与 dev 内容相同。

**Step 6: 验证目录结构**

```bash
find deploy/configmap -type f | sort
```
Expected:
```
deploy/configmap/dev/auth.yml
deploy/configmap/dev/infra.yml
deploy/configmap/dev/seata.yml
deploy/configmap/prod/auth.yml
deploy/configmap/prod/infra.yml
deploy/configmap/prod/seata.yml
deploy/configmap/test/auth.yml
deploy/configmap/test/infra.yml
deploy/configmap/test/seata.yml
```

---

## Task 7: 精简 erp-gateway 的 application.yml

**Files:**
- Modify: `erp-services/erp-gateway/src/main/resources/application.yml`

**当前内容分析：**
- 需删除：`sa-token` 全块（移入 ConfigMap auth.yml）、`management` 全块（移入 web-defaults.yml）、`logging.pattern` （移入 web-defaults.yml）、`data.redis.lettuce.pool` 块（移入 redis-defaults.yml）
- 网关特殊：actuator 需要额外暴露 `gateway` 端点，需在服务自己的 yml 覆盖
- 需保留：`data.redis` 的 host/port/password 占位符（等 ConfigMap 挂载后可删，此处保留本地开发兜底）
- 需添加：`spring.config.import`

**精简后内容：**

```yaml
spring:
  application:
    name: erp-gateway
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"

server:
  port: 9000

# 网关需额外暴露 gateway 端点（覆盖 web-defaults.yml 中的默认值）
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info,gateway

logging:
  level:
    com.erp: DEBUG
    org.springframework.cloud.gateway: INFO
```

**Step 1: 修改文件**（用上述内容完整替换现有文件内容）

**Step 2: 验证文件行数合理**
```bash
wc -l erp-services/erp-gateway/src/main/resources/application.yml
```
Expected: 约 20 行（从原来 49 行减少到约 20 行）

---

## Task 8: 精简 erp-auth 的 application.yml

**Files:**
- Modify: `erp-services/erp-auth/src/main/resources/application.yml`

**当前内容分析：**
- 需删除：`sa-token` 全块、`management` 全块、`logging.pattern`、`data.redis.lettuce.pool`、`mybatis-plus.configuration.map-underscore-to-camel-case`、`mybatis-plus.global-config`
- 需保留：`datasource`（含 HikariCP 连接池）、`data.redis` host/port/password
- 需添加：`spring.config.import`、`mybatis-plus.type-aliases-package`

**精简后内容：**

```yaml
spring:
  application:
    name: erp-auth
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

server:
  port: 8080

mybatis-plus:
  type-aliases-package: com.erp.auth.domain.entity

logging:
  level:
    com.erp: DEBUG
```

---

## Task 9: 精简 erp-system 的 application.yml

**Files:**
- Modify: `erp-services/erp-system/src/main/resources/application.yml`

**精简后内容：**

```yaml
spring:
  application:
    name: erp-system
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

server:
  port: 8080

mybatis-plus:
  type-aliases-package: com.erp.system.domain.entity

logging:
  level:
    com.erp: DEBUG
```

---

## Task 10: 精简 erp-base 的 application.yml

**Files:**
- Modify: `erp-services/erp-base/src/main/resources/application.yml`

**当前问题：**
- `sa-token.token-style: terse-uuid` → 改为由 ConfigMap auth.yml 统一（jwt-default）
- `sa-token.is-share: false` → 统一由 ConfigMap 管理
- Redis 端口硬编码 `6379` → 移入 ConfigMap
- `data.redis` 含本地开发用密码 `Erp@redis2024` → 统一为空密码本地兜底
- `mybatis-plus` 重复配置块 → 删除（由 Layer 1 JAR 提供）
- `management` 重复块 → 删除（由 Layer 1 JAR 提供）
- `feign` 配置 → 删除（由 Layer 1 JAR 提供，注意 erp-base 用旧式 `feign.client` 而非 `spring.cloud.openfeign`）

**精简后内容：**

```yaml
server:
  port: 8080

spring:
  application:
    name: erp-base
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
      - "optional:file:/config/seata.yml"
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_base?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000

seata:
  application-id: ${spring.application.name}
  tx-service-group: erp-base-group
  service:
    vgroup-mapping:
      erp-base-group: default

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.erp.base.domain.entity
  global-config:
    db-config:
      id-type: assign_id

logging:
  level:
    com.erp.base: DEBUG
```

> 注意：`mybatis-plus.global-config.db-config.id-type: assign_id` 覆盖了 Layer 1 JAR 默认的 `assign_id`（相同，但明确声明）。如需与 JAR 默认完全一致可删除。

---

## Task 11: 精简 erp-purchase 的 application.yml

**Files:**
- Modify: `erp-services/erp-purchase/src/main/resources/application.yml`

**精简后内容：**

```yaml
server:
  port: 8080

spring:
  application:
    name: erp-purchase
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
      - "optional:file:/config/seata.yml"
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_purchase?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000

seata:
  application-id: ${spring.application.name}
  tx-service-group: erp-purchase-group
  service:
    vgroup-mapping:
      erp-purchase-group: default

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.erp.purchase.domain.entity
  global-config:
    db-config:
      id-type: assign_id

logging:
  level:
    com.erp.purchase: DEBUG
```

---

## Task 12: 精简 erp-sale 的 application.yml

**Files:**
- Modify: `erp-services/erp-sale/src/main/resources/application.yml`

**精简后内容：**

```yaml
spring:
  application:
    name: erp-sale
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
      - "optional:file:/config/seata.yml"
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_sale?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

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
  type-aliases-package: com.erp.sale.domain.entity

logging:
  level:
    com.erp: DEBUG
    io.seata: INFO
```

---

## Task 13: 精简 erp-inventory 的 application.yml

**Files:**
- Modify: `erp-services/erp-inventory/src/main/resources/application.yml`

**注意：** erp-inventory 的 Redis lettuce pool 是自定义值（max-active: 16, max-idle: 8），**保留**并覆盖 Layer 1 JAR 默认值。Resilience4j 配置是服务专属，保留。

**精简后内容：**

```yaml
server:
  port: 8080

spring:
  application:
    name: erp-inventory
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
      - "optional:file:/config/seata.yml"
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_inventory?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
  data:
    redis:
      # 库存服务高并发，覆盖 JAR 默认连接池大小
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2

seata:
  application-id: ${spring.application.name}
  tx-service-group: erp-inventory-group
  service:
    vgroup-mapping:
      erp-inventory-group: default

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.erp.inventory.domain.entity
  global-config:
    db-config:
      id-type: assign_id

# Resilience4j 熔断（服务专属，保留）
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
        minimum-number-of-calls: 5
    instances:
      baseFeignClient:
        base-config: default

logging:
  level:
    com.erp.inventory: DEBUG
    io.seata: INFO
```

---

## Task 14: 精简 erp-finance 的 application.yml

**Files:**
- Modify: `erp-services/erp-finance/src/main/resources/application.yml`

**精简后内容：**

```yaml
server:
  port: 8080

spring:
  application:
    name: erp-finance
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
      - "optional:file:/config/seata.yml"
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_finance?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000

seata:
  application-id: ${spring.application.name}
  tx-service-group: erp-finance-group
  service:
    vgroup-mapping:
      erp-finance-group: default

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.erp.finance.domain.entity
  global-config:
    db-config:
      id-type: assign_id

logging:
  level:
    com.erp.finance: DEBUG
```

---

## Task 15: 精简 erp-production 的 application.yml

**Files:**
- Modify: `erp-services/erp-production/src/main/resources/application.yml`

**精简后内容：**

```yaml
server:
  port: 8080

spring:
  application:
    name: erp-production
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
      - "optional:file:/config/seata.yml"
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/erp_production?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.erp.production.domain.entity
  global-config:
    db-config:
      id-type: assign_id

logging:
  level:
    com.erp.production: DEBUG
```

> 注意：erp-production 当前没有 seata 配置，但按设计表引入 seata.yml。如果生产服务不需要分布式事务，可移除 `seata.yml` import 条目。

---

## Task 16: 精简 erp-report 的 application.yml

**Files:**
- Modify: `erp-services/erp-report/src/main/resources/application.yml`

**注意：** erp-report 使用 Doris 数据库（只读），连接池配置不同（max 10, min 2, read-only: true），保留。

**精简后内容：**

```yaml
spring:
  application:
    name: erp-report
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"
  # 主数据源（Doris，查询专用）
  datasource:
    url: ${DORIS_URL:jdbc:mysql://localhost:9030/erp_dw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DORIS_USERNAME:root}
    password: ${DORIS_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      read-only: true

server:
  port: 8080

mybatis-plus:
  type-aliases-package: com.erp.report.domain

logging:
  level:
    com.erp: DEBUG
```

---

## Task 17: 编译验证

**Steps:**

**Step 1: 编译 erp-commons**

```bash
cd /home/lolo/javaproject/simple/erp-platform
mvn compile -DskipTests -pl erp-commons -am 2>&1 | tail -30
```
Expected: `BUILD SUCCESS`

**Step 2: 编译 erp-apis**

```bash
mvn compile -DskipTests -pl erp-apis -am 2>&1 | tail -30
```
Expected: `BUILD SUCCESS`

**Step 3: 检查 AutoConfiguration 注册文件是否打包正确**

```bash
find erp-commons -name "org.springframework.boot.autoconfigure.AutoConfiguration.imports" | xargs cat
```
Expected: 输出包含以下四行（各模块）：
```
com.erp.common.mybatis.config.MybatisDefaultsAutoConfiguration
com.erp.common.web.config.WebDefaultsAutoConfiguration
com.erp.common.mq.config.RabbitmqDefaultsAutoConfiguration
com.erp.common.redis.config.RedisDefaultsAutoConfiguration
```

**Step 4: 检查 erp-defaults YAML 文件是否存在**

```bash
find erp-commons -path "*/erp-defaults/*.yml" | sort
```
Expected:
```
erp-commons/erp-common-mq/src/main/resources/erp-defaults/rabbitmq-defaults.yml
erp-commons/erp-common-mybatis/src/main/resources/erp-defaults/mybatis-defaults.yml
erp-commons/erp-common-redis/src/main/resources/erp-defaults/redis-defaults.yml
erp-commons/erp-common-web/src/main/resources/erp-defaults/web-defaults.yml
```

---

## Task 18: Git 提交

**Steps:**

**Step 1: 查看变更文件**

```bash
git status
git diff --stat
```

**Step 2: 提交**

```bash
git add \
  erp-commons/erp-common-core/src/main/java/com/erp/common/core/config/ \
  erp-commons/erp-common-mybatis/src/main/resources/ \
  erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/config/MybatisDefaultsAutoConfiguration.java \
  erp-commons/erp-common-web/src/main/resources/ \
  erp-commons/erp-common-web/src/main/java/com/erp/common/web/config/WebDefaultsAutoConfiguration.java \
  erp-commons/erp-common-mq/src/main/resources/ \
  erp-commons/erp-common-mq/src/main/java/com/erp/common/mq/config/RabbitmqDefaultsAutoConfiguration.java \
  erp-commons/erp-common-redis/src/main/resources/ \
  erp-commons/erp-common-redis/src/main/java/com/erp/common/redis/config/RedisDefaultsAutoConfiguration.java \
  deploy/configmap/ \
  erp-services/erp-gateway/src/main/resources/application.yml \
  erp-services/erp-auth/src/main/resources/application.yml \
  erp-services/erp-system/src/main/resources/application.yml \
  erp-services/erp-base/src/main/resources/application.yml \
  erp-services/erp-purchase/src/main/resources/application.yml \
  erp-services/erp-sale/src/main/resources/application.yml \
  erp-services/erp-inventory/src/main/resources/application.yml \
  erp-services/erp-finance/src/main/resources/application.yml \
  erp-services/erp-production/src/main/resources/application.yml \
  erp-services/erp-report/src/main/resources/application.yml

git commit -m "refactor: extract shared config to three-layer reuse structure

Layer 1: erp-commons JAR-embedded defaults via @AutoConfiguration + @PropertySource
  - YamlPropertySourceFactory in erp-common-core
  - mybatis-defaults.yml, web-defaults.yml, rabbitmq-defaults.yml, redis-defaults.yml

Layer 2: deploy/configmap/{dev,test,prod} with infra.yml / auth.yml / seata.yml
  - services import via spring.config.import optional:file:/config/*.yml

Layer 3: all 10 service application.yml trimmed to service-specific content only
  - fixed: token-style unified to jwt-default
  - fixed: server.shutdown graceful now via JAR default
  - fixed: local dev passwords unified to 123456 default

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## 附录：各服务精简效果对比

| 服务 | 精简前（行数约） | 精简后（行数约） | 删除内容 |
|------|----------------|----------------|---------|
| erp-gateway | 49 | 20 | sa-token全块、management、logging.pattern、redis pool |
| erp-auth | 64 | 22 | sa-token全块、management、logging.pattern、mybatis-plus重复块、redis pool |
| erp-system | 54 | 20 | sa-token全块、management、logging.pattern、mybatis-plus重复块 |
| erp-base | 71 | 28 | sa-token全块（terse-uuid→jwt-default）、management、feign、mybatis重复块 |
| erp-purchase | 94 | 30 | sa-token全块（terse-uuid→jwt-default）、management、feign、rabbitmq listener、mybatis重复块 |
| erp-sale | 80 | 28 | sa-token全块、management、logging.pattern、rabbitmq全块、mybatis重复块 |
| erp-inventory | 128 | 45 | sa-token全块（terse-uuid→jwt-default）、management、feign、rabbitmq listener、mybatis重复块 |
| erp-finance | 94 | 28 | sa-token全块（terse-uuid→jwt-default）、management、feign、rabbitmq listener、mybatis重复块 |
| erp-production | 82 | 28 | sa-token全块（terse-uuid→jwt-default）、management、feign、rabbitmq listener、mybatis重复块 |
| erp-report | 47 | 22 | sa-token全块、management、logging.pattern、mybatis重复块 |

## 附录：本地开发启动说明（供开发人员参考）

```bash
# 无需任何额外准备，直接启动
java -jar erp-sale.jar

# 启动时配置加载顺序：
# 1. erp-commons JAR 默认值（mybatis、logging、feign、rabbitmq listener、redis pool）
# 2. /config/infra.yml → 文件不存在，optional: 静默跳过
# 3. /config/auth.yml  → 文件不存在，optional: 静默跳过
# 4. application.yml   → 服务专属配置（DB url、HikariCP、seata group）
# 5. 环境变量          → 可随时覆盖任何配置（最高优先级）

# 如需连接非 localhost 的中间件：
export REDIS_HOST=192.168.1.100
export RABBITMQ_HOST=192.168.1.100
java -jar erp-sale.jar
```
