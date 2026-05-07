# ERP 平台三大特性 Code Review

## 背景

本文档包含 ERP 微服务平台三个新特性的完整源码，请对每个特性进行代码审查。

**项目技术栈：**
- Spring Boot 3.2.5 / Spring Cloud 2023.0.4 / Java 17
- Spring Cloud Alibaba 2023.0.1.2（Nacos + Sentinel）
- MyBatis-Plus 3.5.12（含 JSqlParser 4.9）
- Sa-Token 1.40.0（JWT 无状态模式）
- Redis（Redisson + StringRedisTemplate）
- XXL-JOB 3.3.0
- 部署环境：Kubernetes，多租户 Schema 隔离

**三个特性概述：**
1. **Gateway 多端拆分**：单体网关拆分为 PC/App/Open 三个独立部署单元 + 公共库
2. **数据权限（行级安全）**：基于 MyBatis-Plus DataPermissionHandler + Redis 缓存的行级数据过滤
3. **分布式定时任务**：封装 XXL-JOB 为 `erp-common-job` 公共模块，零配置自动接入

---

## Review 维度

请针对每个特性，从以下 6 个维度审查：

1. **正确性**：逻辑是否有 bug？边界条件是否处理？
2. **安全性**：有无注入风险、鉴权漏洞、敏感信息泄露？
3. **性能**：有无 N+1 查询、不必要的反射、Redis 热点 Key？
4. **架构设计**：职责划分是否合理？模块边界是否清晰？
5. **可维护性**：代码可读性、扩展性、异常处理质量？
6. **测试可行性**：哪些逻辑最需要单元测试？如何测试？

---

## 特性一：Gateway 多端拆分

### 1.1 模块结构

```
erp-gateway/
├── pom.xml                     (聚合父模块)
├── erp-gateway-common/         (纯 jar 库，所有端共享)
│   ├── pom.xml
│   └── src/main/java/com/erp/gateway/common/
│       ├── config/SaTokenConfig.java      (@AutoConfiguration)
│       └── filter/
│           ├── AuthGlobalFilter.java      (@Component, order=-200)
│           └── GrayRoutingFilter.java     (@Component, order=-100)
├── erp-gateway-pc/             (PC端，port=9000)
├── erp-gateway-app/            (移动端，port=9001)
└── erp-gateway-open/           (开放平台，port=9002)
```

启动类使用双重扫描：
```java
@SpringBootApplication(scanBasePackages = {
    "com.erp.gateway.common",   // 扫描 @Component 过滤器
    "com.erp.gateway.pc"        // 扫描本端自定义 Bean
})
```
公共库同时通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `@AutoConfiguration`。

---

### 1.2 erp-gateway-common/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <parent>
        <groupId>com.erp</groupId>
        <artifactId>erp-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.erp.gateway</groupId>
    <artifactId>erp-gateway-common</artifactId>
    <packaging>jar</packaging>
    <name>erp-gateway-common</name>
    <description>网关公共库：共享过滤器、配置、常量（不含 Spring Boot 启动类）</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
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
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.erp.commons</groupId>
            <artifactId>erp-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
    </dependencies>
    <!-- 纯 jar 库，不打可执行 fat-jar -->
</project>
```

---

### 1.3 SaTokenConfig.java

```java
package com.erp.gateway.common.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Sa-Token 全局配置（自动装配）
 *
 * 注册 SaReactorFilter 用于 WebFlux 环境下的权限校验。
 * 此处仅做登录状态校验，细粒度权限控制在各业务服务内完成。
 *
 * 通过 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * 自动注入到各端网关启动类。
 */
@AutoConfiguration
public class SaTokenConfig {

    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude("/api/auth/login", "/api/auth/refresh", "/actuator/**")
                .setAuth(obj -> SaRouter.match("/**").check(r -> StpUtil.checkLogin()))
                .setError(e -> "{\"code\":401,\"msg\":\"" + e.getMessage() + "\",\"data\":null}");
    }
}
```

**AutoConfiguration.imports：**
```
com.erp.gateway.common.config.SaTokenConfig
```

---

### 1.4 AuthGlobalFilter.java

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
 * 2. 验证 JWT token 有效性
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

### 1.5 GrayRoutingFilter.java

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
 * 2. Cookie gray_user=true（用于灰度用户群体）
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

### 1.6 PC 端路由配置（application-dev.yml）

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: erp-auth
          uri: http://erp-auth:8080
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1

        - id: erp-system
          uri: http://erp-system:8080
          predicates:
            - Path=/api/system/**
          filters:
            - StripPrefix=1

        - id: erp-base
          uri: http://erp-base:8080
          predicates:
            - Path=/api/base/**
          filters:
            - StripPrefix=1

        - id: erp-purchase
          uri: http://erp-purchase:8080
          predicates:
            - Path=/api/purchase/**
          filters:
            - StripPrefix=1

        - id: erp-sale
          uri: http://erp-sale:8080
          predicates:
            - Path=/api/sale/**
          filters:
            - StripPrefix=1

        - id: erp-inventory
          uri: http://erp-inventory:8080
          predicates:
            - Path=/api/inventory/**
          filters:
            - StripPrefix=1

        - id: erp-finance
          uri: http://erp-finance:8080
          predicates:
            - Path=/api/finance/**
          filters:
            - StripPrefix=1

        - id: erp-production
          uri: http://erp-production:8080
          predicates:
            - Path=/api/production/**
          filters:
            - StripPrefix=1

        - id: erp-report
          uri: http://erp-report:8080
          predicates:
            - Path=/api/report/**
          filters:
            - StripPrefix=1

      globalcors:
        cors-configurations:
          '[/**]':
            allowedOriginPatterns: "*"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
            maxAge: 3600

gateway:
  auth:
    whitelist:
      - /api/auth/login
      - /api/auth/refresh
      - /actuator/**
```

---

### Review 问题（特性一）

**正确性：**
- `SaTokenConfig` 同时通过 `scanBasePackages` 路径扫描（会因 `@AutoConfiguration` 不是 `@Component` 而跳过）和 `AutoConfiguration.imports`（会被处理）注入，请验证：`@AutoConfiguration` 是否会被组件扫描意外加载？
- `AuthGlobalFilter` 中，`SaReactorSyncHolder.setContext(exchange)` 在 `StpUtil.checkLogin()` 抛出异常时，`finally` 块仍会 `clearContext()`——但如果异常之前 `setContext` 已经完成，是否存在上下文泄漏？
- 白名单为空（`whitelist` 未配置）时，`isWhitelisted` 返回 `false`，所有请求都需要 token。这是否是期望行为（应该失败开放还是失败关闭）？
- `erp-gateway-open` 不应该有 `SaReactorFilter`（开放网关用 API Key 验证），但 `AutoConfiguration.imports` 会自动注入它。如何解决开放网关与内网网关鉴权方式不同的问题？

**安全性：**
- `unauthorized()` 方法中，`message` 直接从 `SaTokenException.getMessage()` 取出并拼接进 JSON 字符串。若异常消息包含双引号或特殊字符，是否会破坏 JSON 格式，甚至导致 XSS？
- 灰度路由通过 Cookie `gray_user=true` 触发，客户端可以自行设置此 Cookie 绕过灰度控制，强行进入 v2 服务。这是否是可接受的设计？
- 下游 Header 中直接注入 `X-User-Id`、`X-Tenant-Id`：如果绕过网关直接访问业务服务，是否有额外的防护？
- `allowCredentials: true` 与 `allowedOriginPatterns: "*"` 在 dev 环境同时出现，Spring 会拒绝此配置（CORS 规范不允许 credentials+通配符），会导致 CORS 预检失败。

**性能：**
- `whitelist.stream().anyMatch(...)` 在每次请求都执行，列表较短时无问题，但若白名单很长是否考虑提前编译为前缀树或正则集合？
- `AntPathMatcher` 是有状态对象但无缓存，每次 `pathMatcher.match()` 都从头解析 pattern，高并发下是否有性能问题？

**架构设计：**
- `SaTokenConfig`（`@AutoConfiguration`）和 `AuthGlobalFilter`（`@Component`）都在做 Sa-Token 鉴权，两者职责重叠：`SaReactorFilter` 会在 `AuthGlobalFilter` 之前还是之后执行？是否存在双重校验？
- `erp-gateway-open` 是开放 API 网关（第三方 API Key 模式），但它继承了 `erp-gateway-common` 中的 `SaTokenConfig`（JWT 校验），这两种鉴权方式应如何共存或互斥？

---

## 特性二：数据权限（行级安全）

### 2.1 架构概览

```
请求 → DataScopeFilter（Servlet Filter, @Order(-100)）
         ↓ 从 Redis 加载 DataScopeContext → DataScopeContextHolder（TTL）
              ↓ MyBatis-Plus DataPermissionInterceptor
                   ↓ ErpDataPermissionHandler.getSqlSegment()
                        ↓ 动态拼接 WHERE 条件

登录成功 → DataScopeService.buildAndCache()
            ↓ 查角色 → 计算 minScope → 计算 deptIds → 写 Redis
```

**5 级 scope：**
- 1: 全部（不注入条件）
- 2: 本部门（`dept_id IN (primaryDeptId)`）
- 3: 本部门及下级（WITH RECURSIVE CTE 查子树）
- 4: 仅本人（`create_by = userId`）
- 5: 自定义部门（`sys_role_dept` 配置）

**多角色策略：** 取数值最小的 scope（最宽松权限）

---

### 2.2 DataScope.java（注解）

```java
package com.erp.common.mybatis.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解，标注在 Mapper 方法上，触发行级数据过滤。
 *
 * 示例：
 * @DataScope(deptAlias = "o", userAlias = "o")
 * List<SaleOrder> selectPageByQuery(@Param("query") SaleOrderQuery query);
 *
 * 当 deptAlias 不为空且 scope=2/3/5 时，注入 {deptAlias}.dept_id IN (...) 条件。
 * 当 userAlias 不为空且 scope=4 时，注入 {userAlias}.create_by = userId。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** dept_id 字段所在表的别名（如 "t"、"o"），为空则不做部门维度过滤 */
    String deptAlias() default "";

    /** create_by 字段所在表的别名，为空则不做本人维度过滤 */
    String userAlias() default "";
}
```

---

### 2.3 DataScopeContext.java

```java
package com.erp.common.mybatis.datascope;

import java.io.Serializable;
import java.util.Set;

/**
 * 数据权限上下文 DTO
 *
 * dataScope 含义：
 * 1 - 全部数据，不附加任何过滤条件
 * 2 - 本部门数据
 * 3 - 本部门及所有下级部门数据
 * 4 - 仅本人创建的数据
 * 5 - 自定义部门列表（sys_role_dept 配置）
 */
public class DataScopeContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前用户有效权限档位（多角色取最小值，1 = 最宽松） */
    private Integer dataScope;

    /** 当前用户 ID */
    private Long userId;

    /** 用户主部门 ID */
    private Long primaryDeptId;

    /**
     * 有效部门 ID 集合：
     * scope=2：仅主部门 ID
     * scope=3：主部门 + 所有子孙部门 ID
     * scope=5：角色指定的自定义部门 ID 列表
     */
    private Set<Long> deptIds;

    // 构造函数、getter/setter 省略（实际代码有完整实现）
    public DataScopeContext() {}
    public DataScopeContext(Integer dataScope, Long userId, Long primaryDeptId, Set<Long> deptIds) {
        this.dataScope = dataScope; this.userId = userId;
        this.primaryDeptId = primaryDeptId; this.deptIds = deptIds;
    }
    public Integer getDataScope() { return dataScope; }
    public void setDataScope(Integer dataScope) { this.dataScope = dataScope; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPrimaryDeptId() { return primaryDeptId; }
    public void setPrimaryDeptId(Long primaryDeptId) { this.primaryDeptId = primaryDeptId; }
    public Set<Long> getDeptIds() { return deptIds; }
    public void setDeptIds(Set<Long> deptIds) { this.deptIds = deptIds; }
}
```

---

### 2.4 DataScopeContextHolder.java

```java
package com.erp.common.mybatis.datascope;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 数据权限上下文持有者（TransmittableThreadLocal，线程池安全）
 *
 * 由 DataScopeFilter 在请求开始时写入，在 finally 块中清理。
 * ErpDataPermissionHandler 在 MyBatis 拦截阶段读取。
 */
public final class DataScopeContextHolder {

    private static final TransmittableThreadLocal<DataScopeContext> CONTEXT =
            new TransmittableThreadLocal<>();

    private DataScopeContextHolder() {}

    public static void set(DataScopeContext context) { CONTEXT.set(context); }
    public static DataScopeContext get() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}
```

---

### 2.5 ErpDataPermissionHandler.java

```java
package com.erp.common.mybatis.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.erp.common.mybatis.annotation.DataScope;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ERP 数据权限处理器
 *
 * 逻辑：
 * scope=1 → 全部数据，不注入任何条件
 * scope=2/3/5 → {deptAlias}.dept_id IN (deptIds)
 * scope=4 → {userAlias}.create_by = userId
 *
 * 降级规则：
 * deptAlias 为空但 scope=2/3/5 → 使用 userAlias.create_by 过滤
 * DataScopeContext 为 null → 不注入任何条件（内部调用/未登录豁免）
 */
public class ErpDataPermissionHandler implements DataPermissionHandler {

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        DataScopeContext ctx = DataScopeContextHolder.get();
        if (ctx == null) {
            return where;
        }

        DataScope annotation = findAnnotation(mappedStatementId);
        if (annotation == null) {
            return where;
        }

        Integer scope = ctx.getDataScope();
        if (scope == null || scope == 1) {
            return where;
        }

        Expression dataScopeExpr = buildExpression(scope, ctx, annotation);
        if (dataScopeExpr == null) {
            return where;
        }

        return where == null ? dataScopeExpr : new AndExpression(where, dataScopeExpr);
    }

    private Expression buildExpression(Integer scope, DataScopeContext ctx, DataScope annotation) {
        String deptAlias = annotation.deptAlias();
        String userAlias = annotation.userAlias();

        switch (scope) {
            case 2:
            case 3:
            case 5: {
                Set<Long> deptIds = ctx.getDeptIds();
                if (deptAlias != null && !deptAlias.isBlank() && deptIds != null && !deptIds.isEmpty()) {
                    return buildInExpression(deptAlias + ".dept_id", deptIds);
                }
                // 降级：deptAlias 为空时使用 create_by 过滤
                if (userAlias != null && !userAlias.isBlank() && ctx.getUserId() != null) {
                    return buildEqualsExpression(userAlias + ".create_by", ctx.getUserId());
                }
                return null;
            }
            case 4: {
                if (userAlias != null && !userAlias.isBlank() && ctx.getUserId() != null) {
                    return buildEqualsExpression(userAlias + ".create_by", ctx.getUserId());
                }
                return null;
            }
            default:
                return null;
        }
    }

    private Expression buildInExpression(String columnName, Set<Long> values) {
        Column column = new Column(columnName);
        List<LongValue> valueExprs = values.stream()
                .map(LongValue::new)
                .collect(Collectors.toList());
        ExpressionList<LongValue> expressionList = new ExpressionList<>(valueExprs);
        return new InExpression(column, expressionList);
    }

    private Expression buildEqualsExpression(String columnName, Long value) {
        Column column = new Column(columnName);
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(new LongValue(value));
        return equalsTo;
    }

    /**
     * 反射查找 Mapper 方法上的 @DataScope 注解
     *
     * @param mappedStatementId 格式：{全限定类名}.{方法名}
     */
    private DataScope findAnnotation(String mappedStatementId) {
        try {
            int lastDot = mappedStatementId.lastIndexOf('.');
            if (lastDot < 0) return null;
            String className = mappedStatementId.substring(0, lastDot);
            String methodName = mappedStatementId.substring(lastDot + 1);
            Class<?> mapperClass = Class.forName(className);
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScope.class)) {
                    return method.getAnnotation(DataScope.class);
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }
}
```

---

### 2.6 DataScopeFilter.java

```java
package com.erp.common.mybatis.datascope;

import com.erp.common.core.constant.HeaderConstants;
import com.erp.common.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 数据权限上下文加载过滤器
 *
 * 从 Redis 加载 DataScopeContext，存入 DataScopeContextHolder。
 * 内部 Feign 调用豁免：检测到 X-Inner-Call: true 时跳过加载。
 * Redis Key 格式：data:scope:{tenantId}:{userId}
 */
@Order(-100)
public class DataScopeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DataScopeFilter.class);
    private static final String REDIS_KEY_PREFIX = "data:scope:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DataScopeFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String innerCall = request.getHeader(HeaderConstants.INNER_CALL);
            if ("true".equalsIgnoreCase(innerCall)) {
                filterChain.doFilter(request, response);
                return;
            }

            loadDataScope();
            filterChain.doFilter(request, response);
        } finally {
            DataScopeContextHolder.clear();
        }
    }

    private void loadDataScope() {
        String tenantId = TenantContextHolder.getTenantId();
        Long userId = TenantContextHolder.getUserId();

        if (tenantId == null || userId == null) {
            return;
        }

        String key = REDIS_KEY_PREFIX + tenantId + ":" + userId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                DataScopeContext ctx = objectMapper.readValue(json, DataScopeContext.class);
                DataScopeContextHolder.set(ctx);
            }
        } catch (Exception e) {
            log.warn("Failed to load DataScopeContext from Redis, key={}: {}", key, e.getMessage());
        }
    }
}
```

---

### 2.7 MybatisPlusConfig.java（含 DataScopeFilter 注册）

```java
package com.erp.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.erp.common.core.context.TenantContextHolder;
import com.erp.common.mybatis.datascope.DataScopeFilter;
import com.erp.common.mybatis.datascope.ErpDataPermissionHandler;
import com.erp.common.mybatis.handler.ErpMetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * MyBatis-Plus 全局配置
 *
 * 拦截器顺序：多租户 → 数据权限 → 分页 → 乐观锁
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户：Schema 级别隔离
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                    @Override
                    public Expression getTenantId() {
                        String schema = TenantContextHolder.getTenantSchema();
                        return new StringValue(schema != null ? schema : "erp_default");
                    }

                    @Override
                    public boolean ignoreTable(String tableName) {
                        return "sys_tenant".equalsIgnoreCase(tableName)
                                || tableName.startsWith("seata_");
                    }
                }
        ));

        // 2. 数据权限（行级 SQL 过滤）
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new ErpDataPermissionHandler()));

        // 3. 分页（MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 4. 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    @Bean
    public MetaObjectHandler erpMetaObjectHandler() {
        return new ErpMetaObjectHandler();
    }

    /**
     * 数据权限过滤器（仅在 StringRedisTemplate 存在时注册）
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public DataScopeFilter dataScopeFilter(StringRedisTemplate redisTemplate,
                                           @Autowired(required = false) ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
        return new DataScopeFilter(redisTemplate, mapper);
    }
}
```

---

### 2.8 DataScopeService.java（erp-system 服务）

```java
package com.erp.system.application.service;

import com.erp.common.mybatis.datascope.DataScopeContext;
import com.erp.system.domain.entity.*;
import com.erp.system.infrastructure.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 数据权限计算服务
 *
 * 职责：
 * 1. 查询用户角色，取最小（最宽松）dataScope
 * 2. 按 scope 规则计算可访问 deptIds
 * 3. 将 DataScopeContext 序列化写入 Redis（TTL 5 分钟）
 *
 * Redis Key 格式：data:scope:{tenantId}:{userId}
 * 调用时机：用户登录成功后、角色/部门变更后
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private static final String REDIS_KEY_PREFIX = "data:scope:";
    private static final long TTL_MINUTES = 5L;

    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserDeptMapper userDeptMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 根据用户信息计算数据权限上下文并写入 Redis
     */
    public void buildAndCache(SysUser user) {
        List<SysRole> roles = roleMapper.findRolesByUserId(user.getId());
        if (roles == null || roles.isEmpty()) {
            cacheContext(user.getTenantId(), user.getId(), buildContext(4, user, List.of()));
            return;
        }

        int minScope = roles.stream()
                .filter(r -> r.getDataScope() != null)
                .mapToInt(SysRole::getDataScope)
                .min()
                .orElse(4);

        DataScopeContext ctx = buildContext(minScope, user, roles);
        cacheContext(user.getTenantId(), user.getId(), ctx);
    }

    /**
     * 主动清除用户的数据权限缓存（角色/部门变更时调用）
     */
    public void evict(String tenantId, Long userId) {
        String key = REDIS_KEY_PREFIX + tenantId + ":" + userId;
        redisTemplate.delete(key);
    }

    private DataScopeContext buildContext(int scope, SysUser user, List<SysRole> roles) {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setDataScope(scope);
        ctx.setUserId(user.getId());
        ctx.setPrimaryDeptId(user.getDeptId());

        Set<Long> deptIds = new HashSet<>();

        switch (scope) {
            case 1:
                break;
            case 2:
                if (user.getDeptId() != null) deptIds.add(user.getDeptId());
                break;
            case 3:
                if (user.getDeptId() != null) {
                    List<Long> subIds = deptMapper.findSubDeptIds(user.getDeptId());
                    deptIds.addAll(subIds);
                }
                break;
            case 4:
                break;
            case 5:
                for (SysRole role : roles) {
                    if (role.getDataScope() != null && role.getDataScope() == 5) {
                        List<Long> roleDeptIds = roleDeptMapper.findDeptIdsByRoleId(role.getId());
                        deptIds.addAll(roleDeptIds);
                    }
                }
                break;
            default:
                break;
        }

        ctx.setDeptIds(deptIds);
        return ctx;
    }

    private void cacheContext(String tenantId, Long userId, DataScopeContext ctx) {
        String key = REDIS_KEY_PREFIX + tenantId + ":" + userId;
        try {
            String json = objectMapper.writeValueAsString(ctx);
            redisTemplate.opsForValue().set(key, json, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DataScopeContext for key={}: {}", key, e.getMessage());
        }
    }
}
```

---

### 2.9 V2__data_permission_tables.sql

```sql
-- V2: 数据权限相关表

CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   VARCHAR(64)  NOT NULL                COMMENT '租户ID',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父部门ID，0表示根节点',
    `dept_name`   VARCHAR(100) NOT NULL                COMMENT '部门名称',
    `sort`        INT          NOT NULL DEFAULT 0      COMMENT '显示排序',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1-启用，0-禁用',
    `create_time` DATETIME     NOT NULL                COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL                COMMENT '更新时间',
    `create_by`   BIGINT                               COMMENT '创建人',
    `update_by`   BIGINT                               COMMENT '更新人',
    `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-正常，1-删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_parent` (`tenant_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

CREATE TABLE IF NOT EXISTS `sys_user_dept` (
    `id`        BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`   BIGINT  NOT NULL               COMMENT '用户ID',
    `dept_id`   BIGINT  NOT NULL               COMMENT '部门ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_dept` (`user_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户部门关联表';

CREATE TABLE IF NOT EXISTS `sys_role_dept` (
    `id`        BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`   BIGINT  NOT NULL               COMMENT '角色ID',
    `dept_id`   BIGINT  NOT NULL               COMMENT '部门ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_dept` (`role_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色自定义数据范围部门表';

ALTER TABLE `sys_user`
    ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT '主部门ID' AFTER `tenant_id`;

ALTER TABLE `sys_role`
    MODIFY COLUMN `data_scope` TINYINT DEFAULT NULL
        COMMENT '数据权限范围：1-全部，2-本部门，3-本部门及下级，4-仅本人，5-自定义部门';
```

---

### Review 问题（特性二）

**正确性：**
- `findAnnotation()` 通过 `Class.forName(className)` + `getMethods()` 遍历查找。若 Mapper 接口有方法重载（同名不同参），只会取到第一个匹配的方法，无法区分。这在 MyBatis 中是否是实际问题？
- scope=3 时，`deptMapper.findSubDeptIds(deptId)` 的 `WITH RECURSIVE CTE` 是否包含根节点自身？如果不包含，`dept_id IN (subIds)` 就会漏掉用户主部门的数据。
- scope=2/3/5 的降级逻辑：当 `deptAlias` 为空但 `deptIds` 不为空时，降级为 `create_by = userId` 过滤。但 scope=3（本部门及下级）的语义是"看到下属的数据"，降级为"仅本人"会导致数据大范围丢失，这个降级逻辑是否合理？
- `DataScopeFilter` 用 `X-Inner-Call: true` 跳过数据权限加载。如果业务代码在异步线程（如 `@Async`）中执行 Mapper，`TransmittableThreadLocal` 能否正确传递上下文？TTL 插件是否正确配置？
- Redis 缓存 TTL 为 5 分钟，若用户在此期间被撤销角色权限，旧的宽松权限最多持续 5 分钟。对 ERP 这类金融系统是否可接受？是否有 evict 的调用约束？

**安全性：**
- `DataScopeFilter` 依赖 `TenantContextHolder.getTenantId()` 和 `getUserId()` 取值，这两个值来自网关注入的请求头（`X-User-Id`、`X-Tenant-Id`）。如果业务服务直接暴露（绕过网关），攻击者可以伪造这两个 Header 从而读取任意用户数据。是否有措施防止直连？
- `X-Inner-Call: true` 可以被任何请求伪造，从而绕过数据权限完全豁免所有过滤。这个内部调用标识是否需要签名或 HMAC 验证？
- `findAnnotation()` 使用 `Class.forName(className)` 加载类。若 `mappedStatementId` 被恶意构造（例如通过 SQL 注入修改 XML），是否存在类加载的安全隐患？

**性能：**
- `findAnnotation()` 每次 SQL 执行都通过反射查找注解，高并发场景下（每个 SQL 都触发 `Class.forName` + `getMethods()` 遍历）是否有性能问题？是否考虑 `ConcurrentHashMap` 缓存结果？
- scope=5（自定义部门）时，`for (SysRole role : roles)` 对每个 scope=5 的角色分别查一次 `roleDeptMapper.findDeptIdsByRoleId()`，若角色数量多则存在 N+1 问题。是否应该 batch 查询？
- `deptIds` 为 `Set<Long>` 传入 `IN` 子句，若部门树非常深（数百个子部门），生成的 `IN` 列表很长，是否会影响 MySQL 查询计划？

**架构设计：**
- `DataScopeContext.primaryDeptId` 字段存在但在 `ErpDataPermissionHandler` 中未被使用（scope=2 直接用 `deptIds`，而 `deptIds` 在 `DataScopeService` 中已经根据 `primaryDeptId` 填充）。这是否是冗余字段，还是预留给某种场景？
- `MybatisPlusConfig` 中 `@ConditionalOnBean(StringRedisTemplate.class)` 控制 `DataScopeFilter` 的注册，但 `ErpDataPermissionHandler` 是无条件注册的（在 `mybatisPlusInterceptor()` 中直接 `new`）。没有 Redis 的服务（例如仅用 MyBatis 但不用 Redis 的场景）会在 `DataScopeContextHolder.get()` 永远返回 null，不注入任何条件——这是有意的兜底行为还是潜在的安全漏洞？

---

## 特性三：分布式定时任务（erp-common-job）

### 3.1 模块结构

```
erp-commons/erp-common-job/
├── pom.xml
└── src/main/
    ├── java/com/erp/common/job/config/
    │   ├── XxlJobConfig.java                      (@Configuration, @ConditionalOnProperty)
    │   └── XxlJobDefaultsAutoConfiguration.java  (@AutoConfiguration, 加载 YAML 默认值)
    └── resources/
        ├── erp-defaults/xxl-job-defaults.yml      (架构级默认配置)
        └── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

**设计意图：** 业务服务只需添加 `erp-common-job` 依赖 + 在 `application.yml` 中配置 `xxl.job.admin.addresses` 即可接入 XXL-JOB，其余配置均有默认值。

---

### 3.2 erp-common-job/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <parent>
        <groupId>com.erp.commons</groupId>
        <artifactId>erp-commons</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>erp-common-job</artifactId>
    <name>erp-common-job</name>
    <description>XXL-JOB 薄封装（依赖收口 + 自动配置），按需引入，不可独立运行</description>

    <dependencies>
        <dependency>
            <groupId>com.xuxueli</groupId>
            <artifactId>xxl-job-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.erp.commons</groupId>
            <artifactId>erp-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

### 3.3 XxlJobDefaultsAutoConfiguration.java

```java
package com.erp.common.job.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 XXL-JOB 架构级默认配置（最低优先级）。
 * 各服务 application.yml 中的同名配置会自动覆盖此处默认值。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/xxl-job-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class XxlJobDefaultsAutoConfiguration {
    // 仅负责属性注入，Bean 定义在 XxlJobConfig
}
```

**AutoConfiguration.imports：**
```
com.erp.common.job.config.XxlJobDefaultsAutoConfiguration
com.erp.common.job.config.XxlJobConfig
```

---

### 3.4 xxl-job-defaults.yml

```yaml
# XXL-JOB Executor 默认配置
# 业务服务只需覆盖 xxl.job.admin.addresses，其余默认值继承此处
xxl:
  job:
    executor:
      appname: ${spring.application.name}
      address: ""
      ip: ""
      port: 9100
      logpath: /data/applogs/xxl-job
      logretentiondays: 30
    accessToken: ${XXL_JOB_ACCESS_TOKEN:}
```

---

### 3.5 XxlJobConfig.java

```java
package com.erp.common.job.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * XXL-JOB Executor 自动配置
 *
 * 仅在配置了 xxl.job.admin.addresses 时生效。
 * 各业务服务只需在 application.yml 中配置：
 *   xxl.job.admin.addresses: http://xxl-job-admin.xxl-job.svc.cluster.local:8080/xxl-job-admin
 * 其余配置项均有默认值。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "xxl.job", name = "admin.addresses")
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    /** 默认取 spring.application.name，各服务无需手动配置 appname */
    @Value("${xxl.job.executor.appname:${spring.application.name}}")
    private String appname;

    /** 留空：框架自动取 Pod eth0 IP 上报给 Admin，适配 K8s 容器环境 */
    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    /** 统一端口 9100，K8s 容器部署 Pod IP 不同，端口统一无冲突 */
    @Value("${xxl.job.executor.port:9100}")
    private int port;

    @Value("${xxl.job.executor.logpath:/data/applogs/xxl-job}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        if (StringUtils.hasText(address)) {
            executor.setAddress(address);
        }

        String resolvedIp = resolveExecutorIp();
        if (StringUtils.hasText(resolvedIp)) {
            executor.setIp(resolvedIp);
        }
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);

        log.info("XXL-JOB Executor initialized: appname={}, ip={}, port={}, admin={}",
                appname, resolvedIp, port, adminAddresses);
        return executor;
    }

    private String resolveExecutorIp() {
        if (StringUtils.hasText(ip)) {
            return ip.trim();
        }

        String podIp = System.getenv("POD_IP");
        if (isUsableIp(podIp)) {
            return podIp.trim();
        }

        String hostIp = System.getenv("HOST_IP");
        if (isUsableIp(hostIp)) {
            return hostIp.trim();
        }

        String localIp = findFirstSiteLocalIpv4();
        if (StringUtils.hasText(localIp)) {
            return localIp;
        }

        return null;
    }

    private boolean isUsableIp(String candidate) {
        if (!StringUtils.hasText(candidate)) return false;
        String ipValue = candidate.trim();
        return !"127.0.0.1".equals(ipValue) && !"0.0.0.0".equals(ipValue);
    }

    private String findFirstSiteLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Resolve XXL-JOB executor ip failed, fallback to framework default behavior", ex);
        }
        return null;
    }
}
```

---

### Review 问题（特性三）

**正确性：**
- `XxlJobDefaultsAutoConfiguration` 和 `XxlJobConfig` 都注册在 `AutoConfiguration.imports` 中，但 `XxlJobConfig` 还标注了 `@ConditionalOnProperty`。Spring Boot 自动配置处理顺序：`XxlJobDefaultsAutoConfiguration` 先加载（注入 YAML 默认属性），再处理 `XxlJobConfig`（此时才检查条件）。这个依赖顺序是否有声明（`@AutoConfigureAfter` 或顺序保证）？若顺序反转，`@ConditionalOnProperty` 在属性注入前求值，将永远返回 false。
- `resolveExecutorIp()` 优先级：`ip` 配置 → `POD_IP` 环境变量 → `HOST_IP` 环境变量 → 网卡探测。在 K8s 中 `POD_IP` 是标准注入方式，但若用户同时配置了 `xxl.job.executor.ip` 为占位符（如 `${MY_POD_IP}`）而环境变量未注入，`@Value` 会抛 `IllegalArgumentException` 而非降级。
- `findFirstSiteLocalIpv4()` 遍历所有网络接口取第一个非 loopback、非虚拟的 IPv4 地址。在 K8s Pod 中通常有 eth0（Pod IP）和 lo，顺序不保证。是否应该优先选择 eth0？
- `logPath` 默认为 `/data/applogs/xxl-job`，本地开发环境（macOS/Windows）该路径不存在，XXL-JOB 启动时会报错还是自动创建？

**安全性：**
- `accessToken` 通过 `${XXL_JOB_ACCESS_TOKEN:}` 注入，默认值为空字符串。XXL-JOB 在 `accessToken` 为空时不校验 token，即 Admin 与 Executor 之间的通信无认证。生产环境若忘记注入 `XXL_JOB_ACCESS_TOKEN` 环境变量，任何能访问 9100 端口的人都可以触发任务执行。是否应该让空 token 导致启动失败？
- Executor 端口 9100 统一暴露，XXL-JOB Admin 需要回调 Executor 的 HTTP 接口。在 K8s 中是否需要 NetworkPolicy 限制只有 Admin 能访问 9100？

**性能：**
- `resolveExecutorIp()` 在每次 Bean 初始化时调用一次（启动时），无性能问题。但 `findFirstSiteLocalIpv4()` 遍历所有网络接口有一定开销，是否需要缓存？（实际上只在启动时调用一次，问题不大）
- 多个业务服务共享 `appname = spring.application.name`，每个服务在 XXL-JOB Admin 中注册为独立 Executor Group。若同一服务有多个实例（K8s 水平扩缩容），同一 `appname` 下会注册多个 Executor 地址——XXL-JOB 是否能正确做负载均衡？路由策略是否有建议？

**架构设计：**
- `erp-common-job` 的 `AutoConfiguration.imports` 同时注册了 `XxlJobDefaultsAutoConfiguration`（无条件）和 `XxlJobConfig`（有条件）。未配置 `xxl.job.admin.addresses` 的服务会加载 YAML 默认属性（无害），但不会创建 Executor Bean——这是期望的零侵入行为。请确认：这种"属性加载 + 条件 Bean"的双层设计是否清晰，还是容易让维护者误以为 `XxlJobDefaultsAutoConfiguration` 会独立触发某些行为？
- 日志目录 `logpath` 在业务服务间统一为 `/data/applogs/xxl-job`，所有服务的日志写在同一路径。在同一宿主机上若多个服务部署（非 K8s 场景），日志是否会互相覆盖？

---

## 跨特性问题

1. **三个特性的鉴权链路**：请求从 Gateway（JWT 校验）→ 业务服务（TenantContextHolder 读取请求头）→ MyBatis（DataScopeFilter 读 Redis）。如果 Redis 不可用，整个链路的降级行为是什么？Gateway 会返回 500 还是继续处理？DataScopeFilter 会跳过还是报错？

2. **Feign 内部调用传播**：当业务服务 A 通过 Feign 调用业务服务 B 时，`X-User-Id`/`X-Tenant-Id` 是否会自动透传？`X-Inner-Call: true` 是否由 Feign 拦截器自动注入？如果没有，B 服务的 `DataScopeFilter` 会加载错误的（null）上下文。

3. **事务边界与数据权限**：如果 `@Transactional` 方法中调用了多个 Mapper 方法（部分有 `@DataScope`，部分没有），数据权限过滤是否一致？`TransmittableThreadLocal` 在同步事务中是否有问题？

4. **多租户 + 数据权限的叠加**：`MybatisPlusConfig` 拦截器顺序是"多租户 → 数据权限"。多租户插件修改了 SQL 后（加了 schema 前缀），数据权限插件再做 `AND dept_id IN` 注入，JSqlParser 解析的是修改后的 SQL 还是原始 SQL？两层插件是否可能产生 SQL 语法冲突？
