# 列级数据权限 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 Jackson 序列化层实现列级数据权限：VO 字段标注 `@ColumnPermission("perm_code")`，无权限的字段返回 `null`，复用现有 `sys_permission` 表和 Redis Session permissions，零新表、零 SQL 业务改动。

**Architecture:** `ColumnPermissionContextHolder`（TTL）存放当前用户权限码 Set，由 `DataScopeFilter` 在请求开始时从 Sa-Token Redis Session 填充；`ColumnPermissionBeanSerializerModifier` 全局注册进 Jackson，对标有 `@ColumnPermission` 的字段在序列化时检查 Holder，无权限则写 `null`。Holder 放 `erp-common-core`（无额外依赖，被 mybatis 和 web 两个模块共同可见）。

**Tech Stack:** Spring Boot 3.2.5、Jackson `BeanSerializerModifier`、Sa-Token 1.40.0 jwt-mixin、TransmittableThreadLocal（Alibaba TTL）、Flyway migration

---

## TodoList

- [ ] Task 1: `erp-common-core` 新增 `ColumnPermissionContextHolder`
- [ ] Task 2: `erp-common-web` 新增 `@ColumnPermission` 注解
- [ ] Task 3: `erp-common-web` 新增 `ColumnPermissionSerializer`
- [ ] Task 4: `erp-common-web` 新增 `ColumnPermissionBeanSerializerModifier`
- [ ] Task 5: `erp-common-web` 新增 `ColumnPermissionJacksonConfig` 并注册自动配置
- [ ] Task 6: `erp-common-mybatis` 修改 `DataScopeFilter`，填充 `ColumnPermissionContextHolder`
- [ ] Task 7: `erp-system` 修改 `SysPermission` entity，新增 `fieldName` 字段
- [ ] Task 8: `erp-system` 修改 `SysPermissionMapper`，扩展 `IN (2,3,4)`
- [ ] Task 9: `erp-system` 新增 V4 migration SQL（`sys_permission` 加 `field_name` 字段）
- [ ] Task 10: 全量编译验证 + 统一提交

---

## Task 1: `erp-common-core` 新增 `ColumnPermissionContextHolder`

**放置原则：** `erp-common-core` 无任何业务依赖，`erp-common-mybatis`（填充方）和 `erp-common-web`（消费方）都依赖它，是唯一不引入循环依赖的位置。

**Files:**
- Create: `erp-commons/erp-common-core/src/main/java/com/erp/common/core/context/ColumnPermissionContextHolder.java`

### Step 1：确认目录存在

```bash
ls erp-commons/erp-common-core/src/main/java/com/erp/common/core/context/
```

期望看到 `TenantContextHolder.java`，说明目录存在，直接创建新文件。

### Step 2：创建 `ColumnPermissionContextHolder.java`

```java
package com.erp.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Collections;
import java.util.Set;

/**
 * 列级权限上下文持有者（TransmittableThreadLocal，线程池安全）
 *
 * <p>由 {@code DataScopeFilter} 在请求开始时从 Sa-Token Redis Session 填充，
 * 在请求结束的 finally 块清理。Jackson 序列化阶段由
 * {@code ColumnPermissionSerializer} 读取，进行字段级权限判断。
 *
 * <p>为 null 表示当前请求不需要列权限过滤（内部 Feign 调用或未登录请求在
 * Gateway 已被拦截）；空 Set 表示已登录但无任何列权限。
 *
 * @author erp
 * @since 1.0.0
 */
public final class ColumnPermissionContextHolder {

    private static final TransmittableThreadLocal<Set<String>> HOLDER =
            new TransmittableThreadLocal<>();

    private ColumnPermissionContextHolder() {}

    /**
     * 写入当前用户的列权限码集合
     *
     * @param permissions 权限码 Set（来自 Sa-Token Redis Session 的 "permissions" key）
     */
    public static void set(Set<String> permissions) {
        HOLDER.set(permissions);
    }

    /**
     * 获取当前用户的列权限码集合
     *
     * @return 权限码 Set；null 表示未设置（内部调用场景）
     */
    public static Set<String> get() {
        return HOLDER.get();
    }

    /**
     * 判断当前用户是否拥有指定列权限码
     *
     * <p>Holder 为 null 时（内部调用）返回 true，直接放行。
     *
     * @param permCode 权限码，对应 sys_permission.perm_code（perm_type=4）
     * @return true 表示有权限（或内部调用）
     */
    public static boolean hasPermission(String permCode) {
        Set<String> perms = HOLDER.get();
        if (perms == null) {
            // 内部 Feign 调用或 DataScopeFilter 未运行的场景，直接放行
            return true;
        }
        return perms.contains(permCode);
    }

    /**
     * 清理当前线程上下文，防止内存泄漏
     * 必须在请求结束的 finally 块中调用
     */
    public static void clear() {
        HOLDER.remove();
    }
}
```

### Step 3：验证

```bash
find erp-commons/erp-common-core/src -name "*.java" | sort
```

期望看到 `ColumnPermissionContextHolder.java` 出现在列表中。

---

## Task 2: `erp-common-web` 新增 `@ColumnPermission` 注解

**Files:**
- Create: `erp-commons/erp-common-web/src/main/java/com/erp/common/web/annotation/ColumnPermission.java`

### Step 1：创建包目录

```bash
mkdir -p erp-commons/erp-common-web/src/main/java/com/erp/common/web/annotation
```

### Step 2：创建 `ColumnPermission.java`

```java
package com.erp.common.web.annotation;

import java.lang.annotation.*;

/**
 * 列级数据权限注解
 *
 * <p>标注在 VO / DTO 字段上，声明查看该字段所需的权限码。
 * 对应 {@code sys_permission.perm_code}（{@code perm_type=4}）。
 *
 * <p>示例：
 * <pre>
 *   public class PurchaseOrderVO {
 *
 *       private String orderNo;   // 无注解，所有人可见
 *
 *       {@literal @}ColumnPermission("purchase:order:view_cost")
 *       private BigDecimal unitCost;   // 无权限时返回 null
 *   }
 * </pre>
 *
 * <p>权限控制由 {@code ColumnPermissionBeanSerializerModifier} 在 Jackson
 * 序列化阶段自动介入，业务代码无感知。
 *
 * @author erp
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ColumnPermission {

    /**
     * 查看该字段所需的权限码
     * 对应 {@code sys_permission.perm_code}，{@code perm_type=4}
     */
    String value();
}
```

### Step 3：验证

```bash
find erp-commons/erp-common-web/src/main/java/com/erp/common/web/annotation -type f
```

期望：`ColumnPermission.java`

---

## Task 3: `erp-common-web` 新增 `ColumnPermissionSerializer`

**Files:**
- Create: `erp-commons/erp-common-web/src/main/java/com/erp/common/web/column/ColumnPermissionSerializer.java`

### Step 1：创建包目录

```bash
mkdir -p erp-commons/erp-common-web/src/main/java/com/erp/common/web/column
```

### Step 2：创建 `ColumnPermissionSerializer.java`

```java
package com.erp.common.web.column;

import com.erp.common.core.context.ColumnPermissionContextHolder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 列级权限序列化器
 *
 * <p>包装原始 {@link JsonSerializer}：
 * <ul>
 *   <li>当前用户拥有权限码 → 委托原始 Serializer 正常输出字段值</li>
 *   <li>当前用户无权限码 → 写出 {@code null}（key 保留，value 置空）</li>
 *   <li>{@code ColumnPermissionContextHolder} 为 null（内部 Feign 调用）→ 直接放行</li>
 * </ul>
 *
 * <p>由 {@link ColumnPermissionBeanSerializerModifier} 在 Jackson 初始化时
 * 自动包装标有 {@code @ColumnPermission} 注解的字段，业务代码无需感知。
 *
 * @param <T> 字段值类型
 * @author erp
 * @since 1.0.0
 */
public class ColumnPermissionSerializer<T> extends JsonSerializer<T> {

    /** 原始序列化器（字段有权限时委托它处理） */
    private final JsonSerializer<T> delegate;

    /** 对应 sys_permission.perm_code（perm_type=4） */
    private final String permCode;

    public ColumnPermissionSerializer(JsonSerializer<T> delegate, String permCode) {
        this.delegate = delegate;
        this.permCode = permCode;
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {

        if (ColumnPermissionContextHolder.hasPermission(permCode)) {
            // 有权限（含内部调用）→ 正常序列化
            delegate.serialize(value, gen, serializers);
        } else {
            // 无权限 → 写 null，key 保留便于前端渲染占位符
            gen.writeNull();
        }
    }
}
```

### Step 3：验证

```bash
find erp-commons/erp-common-web/src/main/java/com/erp/common/web/column -type f
```

期望：`ColumnPermissionSerializer.java`

---

## Task 4: `erp-common-web` 新增 `ColumnPermissionBeanSerializerModifier`

**Files:**
- Create: `erp-commons/erp-common-web/src/main/java/com/erp/common/web/column/ColumnPermissionBeanSerializerModifier.java`

### Step 1：创建 `ColumnPermissionBeanSerializerModifier.java`

```java
package com.erp.common.web.column;

import com.erp.common.web.annotation.ColumnPermission;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * 列级权限 Bean 序列化修饰器
 *
 * <p>在 Jackson 构建 Bean Serializer 时介入（项目启动时执行一次，之后缓存）：
 * 遍历所有属性，将标有 {@code @ColumnPermission} 的字段替换为
 * {@link ColumnPermissionSerializer} 包装版。
 *
 * <p>对所有 VO / DTO / Entity 自动生效，包括嵌套对象和 List 中的元素。
 *
 * @author erp
 * @since 1.0.0
 */
public class ColumnPermissionBeanSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                      BeanDescription beanDesc,
                                                      List<BeanPropertyWriter> beanProperties) {
        for (int i = 0; i < beanProperties.size(); i++) {
            BeanPropertyWriter writer = beanProperties.get(i);
            ColumnPermission annotation = writer.getAnnotation(ColumnPermission.class);
            if (annotation != null) {
                // 用权限序列化器包装原始序列化器
                @SuppressWarnings({"unchecked", "rawtypes"})
                ColumnPermissionSerializer<?> wrapped =
                        new ColumnPermissionSerializer(writer.getSerializer(), annotation.value());
                writer.assignSerializer(wrapped);
            }
        }
        return beanProperties;
    }
}
```

> **注意：** `writer.getSerializer()` 在 Jackson 初始化阶段可能返回 `null`（延迟解析）。
> 这是正常的——`ColumnPermissionSerializer.serialize()` 被调用时，Jackson 已完成类型解析，
> `delegate` 不会是 null。若运行期遇到 NPE，在 `ColumnPermissionSerializer.serialize()` 里
> 加 null 检查并回退到 `serializers.defaultSerializeValue(value, gen)` 即可。

### Step 2：验证

```bash
find erp-commons/erp-common-web/src/main/java/com/erp/common/web/column -type f | sort
```

期望：`ColumnPermissionBeanSerializerModifier.java`、`ColumnPermissionSerializer.java`

---

## Task 5: `erp-common-web` 新增 `ColumnPermissionJacksonConfig` 并注册自动配置

**Files:**
- Create: `erp-commons/erp-common-web/src/main/java/com/erp/common/web/column/ColumnPermissionJacksonConfig.java`
- Modify: `erp-commons/erp-common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Step 1：创建 `ColumnPermissionJacksonConfig.java`

```java
package com.erp.common.web.column;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 列级权限 Jackson 自动配置
 *
 * <p>将 {@link ColumnPermissionBeanSerializerModifier} 注册进 Spring 管理的
 * {@link ObjectMapper}，使其对所有通过 Spring MVC 返回的 JSON 响应生效。
 *
 * <p>通过 {@code AutoConfiguration.imports} 自动加载，引入 {@code erp-common-web}
 * 的服务无需任何额外配置。
 *
 * @author erp
 * @since 1.0.0
 */
@AutoConfiguration
public class ColumnPermissionJacksonConfig {

    /**
     * 注册列级权限序列化修饰器
     *
     * <p>Jackson 在构建每个类的序列化器时（启动期，一次性）调用
     * {@code BeanSerializerModifier.changeProperties()}，对标有
     * {@code @ColumnPermission} 的字段替换为包装序列化器。
     */
    @Bean
    public ColumnPermissionBeanSerializerModifier columnPermissionBeanSerializerModifier(
            ObjectMapper objectMapper) {
        ColumnPermissionBeanSerializerModifier modifier = new ColumnPermissionBeanSerializerModifier();
        objectMapper.registerModule(
                new com.fasterxml.jackson.databind.module.SimpleModule()
                        .setSerializerModifier(modifier)
        );
        return modifier;
    }
}
```

### Step 2：追加自动配置注册

当前 `AutoConfiguration.imports` 内容：
```
com.erp.common.web.config.WebDefaultsAutoConfiguration
```

修改为（追加一行）：
```
com.erp.common.web.config.WebDefaultsAutoConfiguration
com.erp.common.web.column.ColumnPermissionJacksonConfig
```

用编辑器或以下命令追加：
```bash
echo "com.erp.common.web.column.ColumnPermissionJacksonConfig" \
  >> erp-commons/erp-common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### Step 3：验证

```bash
cat erp-commons/erp-common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

期望输出（两行）：
```
com.erp.common.web.config.WebDefaultsAutoConfiguration
com.erp.common.web.column.ColumnPermissionJacksonConfig
```

---

## Task 6: `erp-common-mybatis` 修改 `DataScopeFilter`，填充 `ColumnPermissionContextHolder`

**Files:**
- Modify: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/datascope/DataScopeFilter.java`

**目标：** 在请求开始时，从 Sa-Token Redis Session 读取 `permissions` 列表，转为 `HashSet<String>` 写入 `ColumnPermissionContextHolder`；在 `finally` 块追加清理。

**前置知识：** `erp-common-mybatis` 已有 `sa-token-redis-jackson` 的传递依赖（通过使用 Sa-Token 的业务服务引入），`StpUtil.getSessionByLoginId()` 可用。但 `erp-common-mybatis` 本身的 pom 中并无显式 sa-token 依赖，需在过滤器里用 `optional` 方式处理——若 `StpUtil` 不可用则跳过列权限填充（兜底：Holder 为 null，序列化器放行）。实际上业务服务都会引入 `erp-common-auth`，所以运行时 `StpUtil` 一定可用，编译期用 `try-catch ClassNotFoundException` 保护即可。

最简实现：直接调用 `StpUtil`，编译期在 pom 里加 `optional` 依赖，运行时无问题。

### Step 1：在 `erp-common-mybatis` pom 新增 Sa-Token 可选依赖

编辑 `erp-commons/erp-common-mybatis/pom.xml`，在 `</dependencies>` 前追加：

```xml
        <!-- ColumnPermissionContextHolder 填充：读取 Sa-Token Redis Session -->
        <!-- optional=true，不传递给下游；业务服务通过 erp-common-auth 引入实际实现 -->
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-spring-boot3-starter</artifactId>
            <optional>true</optional>
        </dependency>
```

### Step 2：修改 `DataScopeFilter.java`

找到现有文件，进行以下两处改动：

**改动 A — 新增 import（在现有 import 块末尾追加）：**

```java
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.erp.common.core.context.ColumnPermissionContextHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
```

**改动 B — 修改 `doFilterInternal` 方法：**

将：
```java
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            loadDataScope();
            filterChain.doFilter(request, response);
        } finally {
            DataScopeContextHolder.clear();
        }
    }
```

改为：
```java
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            loadDataScope();
            loadColumnPermissions();
            filterChain.doFilter(request, response);
        } finally {
            DataScopeContextHolder.clear();
            ColumnPermissionContextHolder.clear();
        }
    }
```

**改动 C — 在 `loadDataScope()` 方法之后新增 `loadColumnPermissions()` 方法：**

```java
    /**
     * 从 Sa-Token Redis Session 加载当前用户的权限码集合，写入 ColumnPermissionContextHolder。
     *
     * <p>内部 Feign 调用（携带 X-Inner-Call: true）时跳过，保持 Holder 为 null，
     * ColumnPermissionSerializer 遇到 null 时直接放行（hasPermission 返回 true）。
     *
     * <p>userId 为 null（未登录）时跳过；Gateway 已确保非法请求不到达此处。
     */
    private void loadColumnPermissions() {
        // 内部调用豁免：不做列权限过滤
        // （HeaderConstants.INNER_CALL 已在当前请求的 ServletRequest 中，
        //   但 DataScopeFilter 在 Servlet 层，无法直接获取；
        //   兜底：userId 为 null 时自然跳过）
        Long userId = TenantContextHolder.getUserId();
        if (userId == null) {
            return;
        }
        try {
            SaSession session = StpUtil.getSessionByLoginId(userId, false);
            if (session == null) {
                // 未登录或 Session 已过期，写空 Set（序列化时无任何列权限）
                ColumnPermissionContextHolder.set(new HashSet<>());
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> permList = (List<String>) session.get("permissions");
            Set<String> permSet = permList != null ? new HashSet<>(permList) : new HashSet<>();
            ColumnPermissionContextHolder.set(permSet);
        } catch (Exception e) {
            // Sa-Token 不可用或 Redis 异常时降级：Holder 保持 null，序列化放行
            log.warn("Failed to load column permissions from Sa-Token session, userId={}: {}",
                    userId, e.getMessage());
        }
    }
```

### Step 3：验证改动

```bash
grep -n "loadColumnPermissions\|ColumnPermissionContextHolder" \
  erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/datascope/DataScopeFilter.java
```

期望看到 3 处引用：方法调用、clear 调用、私有方法定义。

---

## Task 7: `erp-system` 修改 `SysPermission` entity，新增 `fieldName` 字段

**Files:**
- Modify: `erp-services/erp-system/src/main/java/com/erp/system/domain/entity/SysPermission.java`

### Step 1：在文件末尾（`status` 字段之后）新增 `fieldName` 字段

找到：
```java
    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;
}
```

改为：
```java
    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;

    /**
     * 字段名（perm_type=4 列权限专用）
     *
     * <p>对应业务 VO 中 {@code @ColumnPermission} 注解所保护的字段名，
     * 仅用于管理界面展示和运维排查，不参与鉴权逻辑（鉴权只用 perm_code）。
     * 其他 perm_type 此字段为 NULL。
     */
    private String fieldName;
}
```

### Step 2：验证

```bash
grep -n "fieldName\|field_name" \
  erp-services/erp-system/src/main/java/com/erp/system/domain/entity/SysPermission.java
```

期望看到 `fieldName` 字段定义。

---

## Task 8: `erp-system` 修改 `SysPermissionMapper`，扩展查询范围至 `IN (2,3,4)`

**Files:**
- Modify: `erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysPermissionMapper.java`

**目标：** 列权限（`perm_type=4`）的权限码需要随登录写入 Redis Session，才能在 Jackson 序列化时被 `ColumnPermissionContextHolder` 使用。只需把过滤条件从 `IN (2,3)` 改为 `IN (2,3,4)` 即可，其余逻辑不变。

### Step 1：修改 `findPermCodesByUserId` 查询

找到：
```java
              AND p.perm_type IN (2, 3)
```

改为：
```java
              AND p.perm_type IN (2, 3, 4)
```

### Step 2：验证

```bash
grep -n "perm_type" \
  erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysPermissionMapper.java
```

期望：`AND p.perm_type IN (2, 3, 4)`

---

## Task 9: `erp-system` 新增 V4 migration SQL

**Files:**
- Create: `erp-services/erp-system/src/main/resources/db/migration/V4__column_permission.sql`

### Step 1：确认已有版本

```bash
ls erp-services/erp-system/src/main/resources/db/migration/
```

期望：V2、V3 已存在，新建 V4。

### Step 2：创建 `V4__column_permission.sql`

```sql
-- =====================================================================
-- V4: 列级数据权限
-- 1. sys_permission 表新增 field_name 字段（perm_type=4 列权限专用）
-- 2. 插入采购模块示例列权限记录（仅供参考，实际按业务需求维护）
-- =====================================================================

-- 新增 field_name 字段
ALTER TABLE `sys_permission`
    ADD COLUMN `field_name` VARCHAR(128) NULL
    COMMENT '字段名（perm_type=4 列权限专用，对应 VO 中 @ColumnPermission 保护的字段名；其他类型为 NULL）'
    AFTER `api_method`;

-- =====================================================================
-- 示例：采购订单列权限
-- 将以下记录的 parent_id 替换为实际 sys_permission 表中"采购订单"菜单的 id
-- =====================================================================

-- INSERT INTO `sys_permission`
--     (`tenant_id`, `parent_id`, `perm_name`, `perm_code`, `perm_type`,
--      `field_name`, `sort_order`, `status`, `create_time`, `update_time`, `deleted`)
-- VALUES
--     ('default', #{purchase_order_menu_id}, '查看采购成本价',
--      'purchase:order:view_cost',   4, 'unitCost', 1, 1, NOW(), NOW(), 0),
--     ('default', #{purchase_order_menu_id}, '查看采购利润',
--      'purchase:order:view_profit', 4, 'profit',   2, 1, NOW(), NOW(), 0);
```

> **说明：** 示例 INSERT 已注释，`parent_id` 需对应实际数据。实际接入时在各业务模块的 migration 里单独维护列权限数据，V4 只做结构变更。

### Step 3：验证

```bash
ls erp-services/erp-system/src/main/resources/db/migration/
```

期望：V2、V3、V4 三个文件。

---

## Task 10: 全量编译验证 + 统一提交

### Step 1：编译 `erp-common-core`

```bash
mvn clean install -pl erp-commons/erp-common-core -am -DskipTests -q
```

期望：`BUILD SUCCESS`

### Step 2：编译 `erp-common-web`

```bash
mvn clean install -pl erp-commons/erp-common-web -am -DskipTests -q
```

期望：`BUILD SUCCESS`

### Step 3：编译 `erp-common-mybatis`

```bash
mvn clean install -pl erp-commons/erp-common-mybatis -am -DskipTests -q
```

期望：`BUILD SUCCESS`

### Step 4：编译 `erp-system`

```bash
mvn clean install -pl erp-services/erp-system -am -DskipTests -q
```

期望：`BUILD SUCCESS`

### Step 5：全量编译

```bash
mvn clean compile -DskipTests -q
```

期望：`BUILD SUCCESS`，无任何编译错误或警告（若有 `unchecked` 警告属正常，来自泛型擦除）。

### Step 6：提交

```bash
git add \
  erp-commons/erp-common-core/src/main/java/com/erp/common/core/context/ColumnPermissionContextHolder.java \
  erp-commons/erp-common-web/src/main/java/com/erp/common/web/annotation/ColumnPermission.java \
  erp-commons/erp-common-web/src/main/java/com/erp/common/web/column/ \
  erp-commons/erp-common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
  erp-commons/erp-common-mybatis/pom.xml \
  erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/datascope/DataScopeFilter.java \
  erp-services/erp-system/src/main/java/com/erp/system/domain/entity/SysPermission.java \
  erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysPermissionMapper.java \
  erp-services/erp-system/src/main/resources/db/migration/V4__column_permission.sql

git commit -m "feat: 实现列级数据权限（@ColumnPermission + Jackson BeanSerializerModifier）

erp-common-core 新增 ColumnPermissionContextHolder（TTL）；
erp-common-web 新增 @ColumnPermission 注解、ColumnPermissionSerializer、
ColumnPermissionBeanSerializerModifier、ColumnPermissionJacksonConfig（自动注册）；
erp-common-mybatis DataScopeFilter 在请求开始时填充列权限上下文；
erp-system SysPermission 新增 fieldName 字段，perm_type 扩展至 4，
SysPermissionMapper 查询范围扩展至 IN (2,3,4)，V4 migration 加 field_name 列。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## 验收标准

| 场景 | 期望行为 |
|------|---------|
| 用户拥有 `purchase:order:view_cost` 权限 | `unitCost` 字段返回真实值 |
| 用户无 `purchase:order:view_cost` 权限 | `unitCost` 字段返回 `null`，key 保留 |
| `List<PurchaseOrderVO>` 返回多条 | 每条记录均按权限过滤，自动生效 |
| 嵌套 VO 中的 `@ColumnPermission` 字段 | 同样被过滤，`BeanSerializerModifier` 对所有 Bean 类型生效 |
| 内部 Feign 调用（`X-Inner-Call: true`） | `ColumnPermissionContextHolder` 为 null，`hasPermission` 返回 true，字段正常输出 |
| 角色被授予/撤销列权限后调用 `PermissionCacheService.refresh(userId)` | Redis Session 权限码实时更新，下次请求立即生效 |
| 全量 Maven 编译 | `BUILD SUCCESS`，零错误 |

---

## 业务服务接入速查

接入新模块的列权限只需三步，**不改任何 Service / Mapper / SQL**：

```java
// 1. VO 字段加注解
@ColumnPermission("sale:order:view_cost")
private BigDecimal cost;
```

```sql
-- 2. sys_permission 插入记录（perm_type=4）
INSERT INTO sys_permission (tenant_id, parent_id, perm_name, perm_code,
    perm_type, field_name, sort_order, status, create_time, update_time, deleted)
VALUES ('default', #{menu_id}, '查看销售成本',
    'sale:order:view_cost', 4, 'cost', 1, 1, NOW(), NOW(), 0);
```

```
3. 系统管理 → 角色管理 → 为对应角色勾选该权限 → 生效
```
