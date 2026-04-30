# Gateway 多端拆分实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 `erp-services/erp-gateway` 拆分为顶层 `erp-gateway/` 父模块，共享逻辑放入 `erp-gateway-common`，PC 端网关迁移到 `erp-gateway-pc`，新增 App/Open 骨架。

**Architecture:** 新建根级 `erp-gateway/` 脱离 `erp-services`，共用过滤器抽入 `erp-gateway-common`（jar），各端网关独立 Spring Boot 应用，当前只实现 `erp-gateway-pc`，`erp-gateway-app` / `erp-gateway-open` 建骨架不部署。

**Tech Stack:** Spring Cloud Gateway (WebFlux)、Sa-Token 1.38.0 Reactor、Spring Boot 3.2.5、Maven 多模块

---

## TodoList

- [ ] Task 1: 新建 `erp-gateway/` 父模块（pom）
- [ ] Task 2: 新建 `erp-gateway-common/` 模块（pom + 目录结构）
- [ ] Task 3: 迁移 `AuthGlobalFilter` 到 `erp-gateway-common`
- [ ] Task 4: 迁移 `GrayRoutingFilter` 到 `erp-gateway-common`
- [ ] Task 5: 迁移 `SaTokenConfig` 到 `erp-gateway-common`
- [ ] Task 6: 新建 `erp-gateway-pc/` 模块（pom + 主类 + 配置文件）
- [ ] Task 7: 新建 `erp-gateway-app/` 骨架
- [ ] Task 8: 新建 `erp-gateway-open/` 骨架
- [ ] Task 9: 更新根 `pom.xml`，注册 `erp-gateway` 模块
- [ ] Task 10: 更新 `erp-services/pom.xml`，移除 `erp-gateway` 子模块
- [ ] Task 11: 删除旧 `erp-services/erp-gateway/` 目录
- [ ] Task 12: 全量编译验证

---

## Task 1: 新建 `erp-gateway/` 父模块

**Files:**
- Create: `erp-gateway/pom.xml`

**Step 1: 创建目录**

```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway
```

**Step 2: 写入父 pom**

创建 `erp-gateway/pom.xml`，内容如下：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.erp</groupId>
        <artifactId>erp-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>erp-gateway</artifactId>
    <packaging>pom</packaging>
    <name>erp-gateway</name>
    <description>网关聚合父模块</description>

    <modules>
        <module>erp-gateway-common</module>
        <module>erp-gateway-pc</module>
        <module>erp-gateway-app</module>
        <module>erp-gateway-open</module>
    </modules>
</project>
```

**Step 3: 验证文件存在**

```bash
cat /home/lolo/javaproject/simple/erp-platform/erp-gateway/pom.xml
```

Expected: 文件内容正常输出。

---

## Task 2: 新建 `erp-gateway-common/` 模块

**Files:**
- Create: `erp-gateway/erp-gateway-common/pom.xml`
- Create: `erp-gateway/erp-gateway-common/src/main/java/com/erp/gateway/common/filter/` (目录)
- Create: `erp-gateway/erp-gateway-common/src/main/java/com/erp/gateway/common/config/` (目录)
- Create: `erp-gateway/erp-gateway-common/src/main/resources/META-INF/spring/` (目录)

**Step 1: 创建目录结构**

```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-common/src/main/java/com/erp/gateway/common/filter
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-common/src/main/java/com/erp/gateway/common/config
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-common/src/main/resources/META-INF/spring
```

**Step 2: 写入 pom.xml**

创建 `erp-gateway/erp-gateway-common/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.erp</groupId>
        <artifactId>erp-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>erp-gateway-common</artifactId>
    <name>erp-gateway-common</name>
    <description>网关公共库（过滤器、Sa-Token配置），packaging=jar，不可独立运行</description>

    <dependencies>
        <!-- Gateway（WebFlux，不可引入 spring-boot-starter-web） -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <!-- Sa-Token Reactor（WebFlux专用） -->
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-reactor-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-jwt</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-redis-jackson</artifactId>
        </dependency>
        <!-- Redis Reactive -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
        <!-- K8s Service DNS 负载均衡 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <!-- Sentinel Gateway 适配器（限流） -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
        </dependency>
        <!-- 公共 core（R/ResultCode/HeaderConstants 等） -->
        <dependency>
            <groupId>com.erp.commons</groupId>
            <artifactId>erp-common-core</artifactId>
        </dependency>
        <!-- Prometheus 指标 -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
    </dependencies>
</project>
```

**注意：** `erp-gateway-common` **不加** `spring-boot-maven-plugin`，它是纯 jar 库。

**Step 3: 写入 AutoConfiguration.imports**

创建 `erp-gateway/erp-gateway-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
com.erp.gateway.common.config.SaTokenConfig
```

---

## Task 3: 迁移 `AuthGlobalFilter`

**Files:**
- Create: `erp-gateway/erp-gateway-common/src/main/java/com/erp/gateway/common/filter/AuthGlobalFilter.java`

**Step 1: 创建文件，复制内容并修改 package**

`package` 改为 `com.erp.gateway.common.filter`，其余代码完全不变：

```java
package com.erp.gateway.common.filter;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.erp.common.core.constant.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sa-Token JWT 鉴权过滤器
 * 1. 白名单路径直接放行
 * 2. 验证JWT token有效性
 * 3. 将用户信息（userId, tenantId, userName）注入到下游请求头
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Value("${gateway.auth.whitelist:}")
    private List<String> whitelist;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        SaReactorSyncHolder.setContext(exchange);

        try {
            StpUtil.checkLogin();

            long userId = StpUtil.getLoginIdAsLong();
            Object tenantId = StpUtil.getExtra("tenantId");
            Object userName = StpUtil.getExtra("userName");

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(HeaderConstants.USER_ID, String.valueOf(userId))
                    .header(HeaderConstants.TENANT_ID, tenantId != null ? tenantId.toString() : "")
                    .header(HeaderConstants.USER_NAME, userName != null ? userName.toString() : "")
                    .build();

            log.debug("Auth passed: userId={}, tenantId={}, path={}", userId, tenantId, path);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (SaTokenException e) {
            log.warn("Auth failed: path={}, error={}", path, e.getMessage());
            return unauthorized(exchange, e.getMessage());
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

    private boolean isWhitelisted(String path) {
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":401,\"msg\":\"%s\",\"data\":null,\"timestamp\":%d}",
                message, System.currentTimeMillis()
        );
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
```

---

## Task 4: 迁移 `GrayRoutingFilter`

**Files:**
- Create: `erp-gateway/erp-gateway-common/src/main/java/com/erp/gateway/common/filter/GrayRoutingFilter.java`

**Step 1: 创建文件，修改 package**

`package` 改为 `com.erp.gateway.common.filter`，其余不变：

```java
package com.erp.gateway.common.filter;

import com.erp.common.core.constant.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 灰度路由过滤器
 * 支持两种灰度触发方式：
 * 1. 请求头 X-Gray-Tag: v2
 * 2. Cookie gray_user=true
 */
@Slf4j
@Component
public class GrayRoutingFilter implements GlobalFilter, Ordered {

    private static final String GRAY_VALUE = "v2";
    private static final String GRAY_COOKIE = "gray_user";

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String existingGrayTag = request.getHeaders().getFirst(HeaderConstants.GRAY_TAG);
        if (GRAY_VALUE.equals(existingGrayTag)) {
            log.debug("Gray routing: request already tagged as v2, path={}", request.getPath().value());
            return chain.filter(exchange);
        }

        boolean isGrayByCookie = request.getCookies()
                .getOrDefault(GRAY_COOKIE, List.of())
                .stream()
                .anyMatch(cookie -> "true".equals(cookie.getValue()));

        if (isGrayByCookie) {
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(HeaderConstants.GRAY_TAG, GRAY_VALUE)
                    .build();
            log.debug("Gray routing: cookie triggered, injecting v2 tag, path={}", request.getPath().value());
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        return chain.filter(exchange);
    }
}
```

---

## Task 5: 迁移 `SaTokenConfig`

**Files:**
- Create: `erp-gateway/erp-gateway-common/src/main/java/com/erp/gateway/common/config/SaTokenConfig.java`

**Step 1: 创建文件，修改 package**

`package` 改为 `com.erp.gateway.common.config`，其余不变：

```java
package com.erp.gateway.common.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 全局配置（WebFlux Reactor 环境）
 */
@Configuration
public class SaTokenConfig {

    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude("/api/auth/login", "/api/auth/refresh", "/actuator/**")
                .setAuth(obj -> {
                    SaRouter.match("/**").check(r -> StpUtil.checkLogin());
                })
                .setError(e -> "{\"code\":401,\"msg\":\"" + e.getMessage() + "\",\"data\":null}");
    }
}
```

---

## Task 6: 新建 `erp-gateway-pc/` 模块

**Files:**
- Create: `erp-gateway/erp-gateway-pc/pom.xml`
- Create: `erp-gateway/erp-gateway-pc/src/main/java/com/erp/gateway/pc/PcGatewayApplication.java`
- Create: `erp-gateway/erp-gateway-pc/src/main/resources/application.yml`
- Create: `erp-gateway/erp-gateway-pc/src/main/resources/application-dev.yml`
- Create: `erp-gateway/erp-gateway-pc/src/main/resources/application-prod.yml`

**Step 1: 创建目录**

```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-pc/src/main/java/com/erp/gateway/pc
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-pc/src/main/resources
```

**Step 2: 写入 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.erp</groupId>
        <artifactId>erp-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>erp-gateway-pc</artifactId>
    <name>erp-gateway-pc</name>
    <description>PC端网关（可运行 Spring Boot 应用）</description>

    <dependencies>
        <dependency>
            <groupId>com.erp</groupId>
            <artifactId>erp-gateway-common</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 3: 写入主类**

创建 `erp-gateway/erp-gateway-pc/src/main/java/com/erp/gateway/pc/PcGatewayApplication.java`：

```java
package com.erp.gateway.pc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.erp.gateway.common", "com.erp.gateway.pc"})
public class PcGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(PcGatewayApplication.class, args);
    }
}
```

**Step 4: 写入 application.yml**（从旧 `erp-gateway/application.yml` 迁移，`spring.application.name` 改为 `erp-gateway-pc`）

```yaml
spring:
  application:
    name: erp-gateway-pc
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "optional:file:/config/infra.yml"
      - "optional:file:/config/auth.yml"

server:
  port: 9000

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

**Step 5: 写入 application-dev.yml**（从旧 `erp-gateway/application-dev.yml` 迁移，内容完全相同）

**Step 6: 写入 application-prod.yml**（从旧 `erp-gateway/application-prod.yml` 迁移，内容完全相同）

---

## Task 7: 新建 `erp-gateway-app/` 骨架

**Files:**
- Create: `erp-gateway/erp-gateway-app/pom.xml`
- Create: `erp-gateway/erp-gateway-app/src/main/java/com/erp/gateway/app/AppGatewayApplication.java`

**Step 1: 创建目录**

```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-app/src/main/java/com/erp/gateway/app
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-app/src/main/resources
```

**Step 2: 写入 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.erp</groupId>
        <artifactId>erp-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>erp-gateway-app</artifactId>
    <name>erp-gateway-app</name>
    <description>移动端网关骨架（暂不部署）</description>

    <dependencies>
        <dependency>
            <groupId>com.erp</groupId>
            <artifactId>erp-gateway-common</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 3: 写入主类**

```java
package com.erp.gateway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.erp.gateway.common", "com.erp.gateway.app"})
public class AppGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppGatewayApplication.class, args);
    }
}
```

**Step 4: 写入最小 application.yml**

创建 `erp-gateway/erp-gateway-app/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: erp-gateway-app

server:
  port: 9001
```

---

## Task 8: 新建 `erp-gateway-open/` 骨架

**Files:**
- Create: `erp-gateway/erp-gateway-open/pom.xml`
- Create: `erp-gateway/erp-gateway-open/src/main/java/com/erp/gateway/open/OpenGatewayApplication.java`

**Step 1: 创建目录**

```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-open/src/main/java/com/erp/gateway/open
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-gateway/erp-gateway-open/src/main/resources
```

**Step 2: 写入 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.erp</groupId>
        <artifactId>erp-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>erp-gateway-open</artifactId>
    <name>erp-gateway-open</name>
    <description>开放平台网关骨架（暂不部署）</description>

    <dependencies>
        <dependency>
            <groupId>com.erp</groupId>
            <artifactId>erp-gateway-common</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 3: 写入主类**

```java
package com.erp.gateway.open;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.erp.gateway.common", "com.erp.gateway.open"})
public class OpenGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenGatewayApplication.class, args);
    }
}
```

**Step 4: 写入最小 application.yml**

```yaml
spring:
  application:
    name: erp-gateway-open

server:
  port: 9002
```

---

## Task 9: 更新根 `pom.xml`，注册 `erp-gateway`

**Files:**
- Modify: `pom.xml`（根目录）

**Step 1: 在 `<modules>` 中新增 `erp-gateway`**

找到：
```xml
    <modules>
        <module>erp-commons</module>
        <module>erp-apis</module>
        <module>erp-services</module>
    </modules>
```

改为：
```xml
    <modules>
        <module>erp-commons</module>
        <module>erp-apis</module>
        <module>erp-services</module>
        <module>erp-gateway</module>
    </modules>
```

**Step 2: 在 `<dependencyManagement>` 中新增 `erp-gateway-common` 版本声明**

在内部公共模块 section 末尾（`erp-common-web` 之后）新增：

```xml
            <dependency>
                <groupId>com.erp</groupId>
                <artifactId>erp-gateway-common</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </dependency>
```

---

## Task 10: 更新 `erp-services/pom.xml`，移除 `erp-gateway`

**Files:**
- Modify: `erp-services/pom.xml`

**Step 1: 移除 `<module>erp-gateway</module>`**

找到：
```xml
        <module>erp-gateway</module>
```

直接删除该行，保留其余模块声明。

**Step 2: 验证**

```bash
grep -n "erp-gateway" /home/lolo/javaproject/simple/erp-platform/erp-services/pom.xml
```

Expected: 无输出（已移除）。

---

## Task 11: 删除旧 `erp-services/erp-gateway/` 目录

**Step 1: 确认新目录文件已就绪**

```bash
find /home/lolo/javaproject/simple/erp-platform/erp-gateway -type f | sort
```

Expected: 看到 common/pc/app/open 各模块的 pom.xml 和 Java 源文件都已存在。

**Step 2: Git 移除旧目录（保留历史）**

```bash
git -C /home/lolo/javaproject/simple/erp-platform rm -r erp-services/erp-gateway/
```

Expected: 输出若干 `rm 'erp-services/erp-gateway/...'` 行，无报错。

---

## Task 12: 全量编译验证

**Step 1: 编译整个项目**

```bash
cd /home/lolo/javaproject/simple/erp-platform && mvn compile -DskipTests 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`，无编译错误。

**Step 2: 单独验证 erp-gateway 模块链**

```bash
cd /home/lolo/javaproject/simple/erp-platform && mvn compile -DskipTests -pl erp-gateway -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`。

**Step 3: 提交**

```bash
git -C /home/lolo/javaproject/simple/erp-platform add erp-gateway/ pom.xml erp-services/pom.xml
git -C /home/lolo/javaproject/simple/erp-platform commit -m "refactor: 将 erp-gateway 从 erp-services 拆分为独立顶层模块

新增 erp-gateway-common（共享过滤器库）、erp-gateway-pc（PC端可运行网关）
以及 erp-gateway-app/erp-gateway-open 骨架，完成多端拆分架构。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```
