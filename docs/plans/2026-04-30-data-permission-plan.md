# 数据权限实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `erp-common-mybatis` 中实现注解驱动的行级数据权限过滤，在 `erp-system` 中新增部门表及数据权限上下文计算逻辑，通过 Redis 缓存向业务服务透明提供数据范围上下文。

**Architecture:** `@DataScope` 注解标注 Mapper 方法 → `DataScopeFilter` 从 Redis 加载 `DataScopeContext` 存入 TTL Holder → `ErpDataPermissionHandler` 在 MyBatis-Plus `DataPermissionInterceptor` 中动态拼接 WHERE 条件。`erp-system` 负责登录时计算并写入 Redis 缓存（`data:scope:{tenantId}:{userId}`，TTL 5 分钟），各业务服务只读缓存。

**Tech Stack:** MyBatis-Plus 3.5.6 DataPermissionInterceptor、TransmittableThreadLocal、Spring Redis（StringRedisTemplate）、Jakarta Servlet Filter、Spring Boot 3.2.5

---

## TodoList

- [ ] Task 1: `erp-common-mybatis` 新增 `@DataScope` 注解
- [ ] Task 2: `erp-common-mybatis` 新增 `DataScopeContext` DTO
- [ ] Task 3: `erp-common-mybatis` 新增 `DataScopeContextHolder`（TTL）
- [ ] Task 4: `erp-common-mybatis` 新增 `ErpDataPermissionHandler`
- [ ] Task 5: `erp-common-mybatis` 新增 `DataScopeFilter`（Servlet Filter）
- [ ] Task 6: `erp-common-mybatis` 更新 `MybatisPlusConfig`，注册 `DataPermissionInterceptor`
- [ ] Task 7: `erp-common-mybatis` 更新 `AutoConfiguration.imports`
- [ ] Task 8: `erp-system` 新增建表 SQL（sys_dept / sys_user_dept / sys_role_dept）
- [ ] Task 9: `erp-system` 新增 `SysRole.dataScope=5` 注释 & `SysUser.deptId` 字段
- [ ] Task 10: `erp-system` 新增部门相关 Entity 及 Mapper
- [ ] Task 11: `erp-system` 新增 `DataScopeService`（计算并写入 Redis）
- [ ] Task 12: `erp-system` 登录流程接入 `DataScopeService`
- [ ] Task 13: 全量编译验证 + commit

---

## Task 1: 新增 `@DataScope` 注解

**Files:**
- Create: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/annotation/DataScope.java`

**Step 1: 创建目录**

```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/annotation
```

**Step 2: 创建注解文件**

```java
package com.erp.common.mybatis.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解 — 标注在 Mapper 方法上，声明 dept_id / create_by 字段的表别名。
 *
 * <p>只有标注了此注解的方法才会被 {@code ErpDataPermissionHandler} 注入 SQL 过滤条件。
 *
 * <p>示例：
 * <pre>
 *   {@literal @}DataScope(deptAlias = "o", userAlias = "o")
 *   List&lt;SaleOrder&gt; selectPageByQuery(@Param("query") SaleOrderQuery query);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * dept_id 字段所在表的别名（如 "t"、"o"）。
     * 留空则对 dataScope=2/3/5 降级为 create_by 过滤。
     */
    String deptAlias() default "";

    /**
     * create_by 字段所在表的别名。
     * 留空则不做本人维度过滤（dataScope=4 时跳过）。
     */
    String userAlias() default "";
}
```

---

## Task 2: 新增 `DataScopeContext` DTO

**Files:**
- Create: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/datascope/DataScopeContext.java`

**Step 1: 创建目录**

```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/datascope
```

**Step 2: 创建文件**

```java
package com.erp.common.mybatis.datascope;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 数据权限上下文 — 承载当前用户的数据范围信息，由 Redis 缓存提供。
 *
 * <p>dataScope 取值：
 * <ul>
 *   <li>1 — 全部数据（不过滤）</li>
 *   <li>2 — 本部门数据</li>
 *   <li>3 — 本部门及下级</li>
 *   <li>4 — 仅本人数据</li>
 *   <li>5 — 自定义部门（角色指定）</li>
 * </ul>
 *
 * <p>多角色取最小 dataScope 值（1 = 最大权限）。
 */
@Data
public class DataScopeContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 有效权限档位（多角色取最小值） */
    private Integer dataScope;

    /** 当前用户 ID */
    private Long userId;

    /** 主部门 ID */
    private Long primaryDeptId;

    /**
     * 有效部门 ID 集合：
     * <ul>
     *   <li>scope=2：仅主部门</li>
     *   <li>scope=3：主部门 + 所有子孙部门</li>
     *   <li>scope=5：角色指定的自定义部门列表</li>
     * </ul>
     */
    private Set<Long> deptIds;
}
```

---

## Task 3: 新增 `DataScopeContextHolder`

**Files:**
- Create: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/datascope/DataScopeContextHolder.java`

**Step 1: 创建文件**

```java
package com.erp.common.mybatis.datascope;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 数据权限上下文持有者（基于 TransmittableThreadLocal，线程池安全）。
 *
 * <p>由 {@code DataScopeFilter} 在请求开始时写入，请求结束时清理。
 * {@code ErpDataPermissionHandler} 在 Mapper 执行时读取。
 */
public final class DataScopeContextHolder {

    private static final TransmittableThreadLocal<DataScopeContext> HOLDER =
            new TransmittableThreadLocal<>();

    private DataScopeContextHolder() {}

    public static void set(DataScopeContext context) {
        HOLDER.set(context);
    }

    public static DataScopeContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
```

---

## Task 4: 新增 `ErpDataPermissionHandler`

**Files:**
- Create: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/datascope/ErpDataPermissionHandler.java`

**Step 1: 创建文件**

```java
package com.erp.common.mybatis.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.erp.common.mybatis.annotation.DataScope;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ERP 数据权限处理器 — 实现 MyBatis-Plus {@code DataPermissionHandler}。
 *
 * <p>只有 Mapper 方法上标注了 {@code @DataScope} 才会介入处理。
 * 根据 {@code DataScopeContext.dataScope} 动态生成 WHERE 片段：
 * <ul>
 *   <li>1 → 不注入任何条件</li>
 *   <li>2 → {deptAlias}.dept_id = {primaryDeptId}</li>
 *   <li>3 → {deptAlias}.dept_id IN (...)</li>
 *   <li>4 → {userAlias}.create_by = {userId}</li>
 *   <li>5 → {deptAlias}.dept_id IN (...)</li>
 * </ul>
 *
 * <p>当 deptAlias 为空但 scope=2/3/5 时，降级为 create_by 过滤。
 * 当 DataScopeContext 为 null（未登录或内部调用）时，不注入任何条件。
 */
@Slf4j
public class ErpDataPermissionHandler implements DataPermissionHandler {

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        // 1. 取上下文
        DataScopeContext ctx = DataScopeContextHolder.get();
        if (ctx == null) {
            return where;
        }

        // 2. 查找 Mapper 方法上的 @DataScope
        DataScope annotation = findAnnotation(mappedStatementId);
        if (annotation == null) {
            return where;
        }

        // 3. 生成过滤条件
        Expression condition = buildCondition(ctx, annotation);
        if (condition == null) {
            return where;
        }

        // 4. 与原有 WHERE 合并
        return (where == null) ? condition : new AndExpression(where, new Parenthesis(condition));
    }

    // ---- private helpers ----

    private DataScope findAnnotation(String mappedStatementId) {
        try {
            int lastDot = mappedStatementId.lastIndexOf('.');
            String className = mappedStatementId.substring(0, lastDot);
            String methodName = mappedStatementId.substring(lastDot + 1);
            Class<?> mapperClass = Class.forName(className);
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScope.class)) {
                    return method.getAnnotation(DataScope.class);
                }
            }
        } catch (Exception e) {
            log.debug("DataScope annotation lookup failed for {}: {}", mappedStatementId, e.getMessage());
        }
        return null;
    }

    private Expression buildCondition(DataScopeContext ctx, DataScope annotation) {
        int scope = ctx.getDataScope() == null ? 1 : ctx.getDataScope();
        String deptAlias = annotation.deptAlias();
        String userAlias = annotation.userAlias();

        return switch (scope) {
            case 1 -> null;  // 全部数据，不过滤
            case 2 -> buildDeptCondition(deptAlias, userAlias, ctx.getUserId(),
                    ctx.getPrimaryDeptId() != null ? Set.of(ctx.getPrimaryDeptId()) : null, true);
            case 3, 5 -> buildDeptCondition(deptAlias, userAlias, ctx.getUserId(),
                    ctx.getDeptIds(), false);
            case 4 -> buildUserCondition(userAlias, ctx.getUserId());
            default -> null;
        };
    }

    /** dept_id = x 或 dept_id IN (x, y, z) */
    private Expression buildDeptCondition(String deptAlias, String userAlias,
                                           Long userId, Set<Long> deptIds, boolean single) {
        if (deptIds == null || deptIds.isEmpty()) {
            // 无部门信息时降级为本人过滤
            return buildUserCondition(userAlias, userId);
        }
        if (deptAlias == null || deptAlias.isBlank()) {
            // 无 deptAlias 时降级为本人过滤
            return buildUserCondition(userAlias, userId);
        }

        String col = deptAlias + ".dept_id";
        if (single || deptIds.size() == 1) {
            EqualsTo eq = new EqualsTo();
            eq.setLeftExpression(new Column(col));
            eq.setRightExpression(new LongValue(deptIds.iterator().next()));
            return eq;
        }
        // IN (...)
        ExpressionList<LongValue> valueList = new ExpressionList<>(
                deptIds.stream().map(LongValue::new).collect(Collectors.toList())
        );
        return new InExpression(new Column(col), valueList);
    }

    /** create_by = userId */
    private Expression buildUserCondition(String userAlias, Long userId) {
        if (userAlias == null || userAlias.isBlank() || userId == null) {
            return null;
        }
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(userAlias + ".create_by"));
        eq.setRightExpression(new LongValue(userId));
        return eq;
    }
}
```

---

## Task 5: 新增 `DataScopeFilter`

**Files:**
- Create: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/datascope/DataScopeFilter.java`

**Step 1: 创建文件**

`erp-common-mybatis` 已有 `erp-common-core` 依赖，`TenantContextHolder` 可直接使用。Spring Data Redis（`StringRedisTemplate`）需通过业务服务引入 `erp-common-redis`，此处通过 `@ConditionalOnBean` 可选注入，避免强依赖。

```java
package com.erp.common.mybatis.datascope;

import com.erp.common.core.constant.HeaderConstants;
import com.erp.common.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 数据权限过滤器
 *
 * <p>请求开始时：从 Redis 加载 {@code DataScopeContext} 存入 {@code DataScopeContextHolder}。
 * 请求结束时：清理 TTL 上下文，防止内存泄漏。
 *
 * <p>内部 Feign 调用（携带 {@code X-Inner-Call: true} 请求头）时跳过加载，
 * 保持 DataScopeContextHolder 为 null，不注入任何数据权限条件。
 *
 * <p>Order = -100，确保在业务 Filter 之前执行。
 */
@Slf4j
@Component
@Order(-100)
@RequiredArgsConstructor
public class DataScopeFilter extends OncePerRequestFilter {

    /** Redis key 前缀，格式：data:scope:{tenantId}:{userId} */
    private static final String KEY_PREFIX = "data:scope:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 内部调用豁免
            String innerCall = request.getHeader(HeaderConstants.INNER_CALL);
            if ("true".equals(innerCall)) {
                filterChain.doFilter(request, response);
                return;
            }

            String tenantId = TenantContextHolder.getTenantId();
            Long userId = TenantContextHolder.getUserId();

            if (tenantId != null && userId != null) {
                String key = KEY_PREFIX + tenantId + ":" + userId;
                String json = stringRedisTemplate.opsForValue().get(key);
                if (json != null) {
                    DataScopeContext ctx = objectMapper.readValue(json, DataScopeContext.class);
                    DataScopeContextHolder.set(ctx);
                    log.debug("DataScopeFilter: loaded scope={} for userId={}", ctx.getDataScope(), userId);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            DataScopeContextHolder.clear();
        }
    }
}
```

**Step 2: 在 `HeaderConstants` 中补充 `INNER_CALL` 常量**

读取 `erp-common-core/src/main/java/com/erp/common/core/constant/HeaderConstants.java`，在末尾新增：

```java
/** 内部服务调用标识（值为 "true" 时跳过数据权限过滤） */
String INNER_CALL = "X-Inner-Call";
```

---

## Task 6: 更新 `MybatisPlusConfig`，注册 `DataPermissionInterceptor`

**Files:**
- Modify: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/config/MybatisPlusConfig.java`

**Step 1: 在拦截器链中插入 `DataPermissionInterceptor`（位置：多租户之后，分页之前）**

找到：
```java
        // 2. 分页（MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
```

在其**前面**插入：
```java
        // 2. 数据权限（行级过滤，在多租户之后）
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new ErpDataPermissionHandler()));
```

需要新增 import：
```java
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.erp.common.mybatis.datascope.ErpDataPermissionHandler;
```

**Step 2: 更新注释**

将 `@param` 注释改为：
```java
     * <p>顺序：多租户 → 数据权限 → 分页 → 乐观锁
```

---

## Task 7: 更新 `AutoConfiguration.imports`

**Files:**
- Modify: `erp-commons/erp-common-mybatis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Step 1: 追加 DataScopeFilter 的自动配置**

当前内容：
```
com.erp.common.mybatis.config.MybatisDefaultsAutoConfiguration
```

改为：
```
com.erp.common.mybatis.config.MybatisDefaultsAutoConfiguration
com.erp.common.mybatis.config.MybatisPlusConfig
com.erp.common.mybatis.datascope.DataScopeFilter
```

**注意：** `MybatisPlusConfig` 是 `@Configuration` 类，需要显式注册确保在非组件扫描环境下也能生效；`DataScopeFilter` 是 `@Component`，同理显式注册。

---

## Task 8: 新增建表 SQL

**Files:**
- Create: `erp-services/erp-system/src/main/resources/db/migration/V2__data_permission_tables.sql`

**Step 1: 确认目录存在**

```bash
ls /home/lolo/javaproject/simple/erp-platform/erp-services/erp-system/src/main/resources/
```

如目录不含 `db/migration`，先创建：
```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-services/erp-system/src/main/resources/db/migration
```

**Step 2: 创建 SQL 文件**

```sql
-- V2: 数据权限相关表

-- 部门表
CREATE TABLE IF NOT EXISTS sys_dept (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '部门ID（雪花）',
    tenant_id   VARCHAR(32)  NOT NULL             COMMENT '租户ID',
    parent_id   BIGINT       NOT NULL DEFAULT 0   COMMENT '父部门ID（0=根节点）',
    ancestors   VARCHAR(512) NOT NULL DEFAULT ''  COMMENT '祖级列表（逗号分隔，用于快速子树查询）',
    dept_name   VARCHAR(64)  NOT NULL             COMMENT '部门名称',
    sort_order  INT          NOT NULL DEFAULT 0   COMMENT '排序号',
    status      TINYINT      NOT NULL DEFAULT 1   COMMENT '状态：1-启用，0-禁用',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    create_by   BIGINT,
    update_by   BIGINT
) COMMENT='部门表';

-- 用户-部门关联表（支持多部门）
CREATE TABLE IF NOT EXISTS sys_user_dept (
    id         BIGINT  NOT NULL PRIMARY KEY,
    user_id    BIGINT  NOT NULL COMMENT '用户ID',
    dept_id    BIGINT  NOT NULL COMMENT '部门ID',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主部门：1-是，0-否',
    UNIQUE KEY uk_user_dept (user_id, dept_id)
) COMMENT='用户-部门关联表';

-- 角色-自定义部门表（dataScope=5 时生效）
CREATE TABLE IF NOT EXISTS sys_role_dept (
    id      BIGINT NOT NULL PRIMARY KEY,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    dept_id BIGINT NOT NULL COMMENT '部门ID',
    UNIQUE KEY uk_role_dept (role_id, dept_id)
) COMMENT='角色-自定义部门关联表';

-- sys_user 新增主部门冗余字段
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS dept_id BIGINT NULL COMMENT '主部门ID（冗余，与 sys_user_dept.is_primary=1 保持同步）';
```

---

## Task 9: 更新 `SysRole.dataScope` 注释 & `SysUser.deptId`

**Files:**
- Modify: `erp-services/erp-system/src/main/java/com/erp/system/domain/entity/SysRole.java`
- Modify: `erp-services/erp-system/src/main/java/com/erp/system/domain/entity/SysUser.java`

**Step 1: 更新 `SysRole.dataScope` 注释**

找到：
```java
    /**
     * 数据权限范围：
     * 1-全部数据，2-本部门数据，3-本部门及以下，4-仅本人数据
     */
    private Integer dataScope;
```

改为：
```java
    /**
     * 数据权限范围：
     * 1-全部数据，2-本部门数据，3-本部门及以下，4-仅本人数据，5-自定义部门
     */
    private Integer dataScope;
```

**Step 2: 在 `SysUser` 末尾新增 `deptId` 字段**

在 `remark` 字段后新增：
```java
    /**
     * 主部门ID（冗余字段，与 sys_user_dept.is_primary=1 保持同步）
     */
    private Long deptId;
```

---

## Task 10: 新增部门相关 Entity 及 Mapper

**Files:**
- Create: `erp-services/erp-system/src/main/java/com/erp/system/domain/entity/SysDept.java`
- Create: `erp-services/erp-system/src/main/java/com/erp/system/domain/entity/SysUserDept.java`
- Create: `erp-services/erp-system/src/main/java/com/erp/system/domain/entity/SysRoleDept.java`
- Create: `erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysDeptMapper.java`
- Create: `erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysUserDeptMapper.java`
- Create: `erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysRoleDeptMapper.java`
- Create: `erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysRoleMapper.java`

**Step 1: 创建 `SysDept`**

```java
package com.erp.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    private String tenantId;
    private Long parentId;
    /** 祖级列表，逗号分隔，用于快速子树查询 */
    private String ancestors;
    private String deptName;
    private Integer sortOrder;
    private Integer status;
}
```

**Step 2: 创建 `SysUserDept`**

```java
package com.erp.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_dept")
public class SysUserDept {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long deptId;
    /** 是否主部门：1-是，0-否 */
    private Integer isPrimary;
}
```

**Step 3: 创建 `SysRoleDept`**

```java
package com.erp.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role_dept")
public class SysRoleDept {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long roleId;
    private Long deptId;
}
```

**Step 4: 创建 `SysDeptMapper`**

```java
package com.erp.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.domain.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 查询某部门的所有子孙部门 ID（含自身）
     * 利用 ancestors 字段的前缀匹配，避免递归查询
     */
    @Select("""
            SELECT id FROM sys_dept
            WHERE deleted = 0 AND status = 1
              AND (id = #{deptId} OR ancestors LIKE CONCAT('%,', #{deptId}, ',%')
                   OR ancestors LIKE CONCAT(#{deptId}, ',%')
                   OR ancestors LIKE CONCAT('%,', #{deptId}))
            """)
    List<Long> findSelfAndDescendantIds(@Param("deptId") Long deptId);
}
```

**Step 5: 创建 `SysUserDeptMapper`**

```java
package com.erp.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.domain.entity.SysUserDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserDeptMapper extends BaseMapper<SysUserDept> {

    /** 查询用户主部门 ID */
    @Select("SELECT dept_id FROM sys_user_dept WHERE user_id = #{userId} AND is_primary = 1 LIMIT 1")
    Long findPrimaryDeptId(@Param("userId") Long userId);
}
```

**Step 6: 创建 `SysRoleDeptMapper`**

```java
package com.erp.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.domain.entity.SysRoleDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDept> {

    /** 查询角色关联的自定义部门 ID 列表 */
    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> findDeptIdsByRoleId(@Param("roleId") Long roleId);
}
```

**Step 7: 创建 `SysRoleMapper`**

```java
package com.erp.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.domain.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 查询用户所有有效角色 */
    @Select("""
            SELECT r.* FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted = 0
            """)
    List<SysRole> findRolesByUserId(@Param("userId") Long userId);
}
```

---

## Task 11: 新增 `DataScopeService`

**Files:**
- Create: `erp-services/erp-system/src/main/java/com/erp/system/application/service/DataScopeService.java`

**Step 1: 创建文件**

```java
package com.erp.system.application.service;

import com.erp.system.domain.entity.SysRole;
import com.erp.system.domain.entity.SysUser;
import com.erp.system.infrastructure.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据权限上下文服务
 *
 * <p>职责：
 * <ol>
 *   <li>登录后计算用户有效数据权限档位（多角色取最小 dataScope）</li>
 *   <li>将 DataScopeContext JSON 写入 Redis，TTL 5 分钟</li>
 *   <li>角色/部门变更时删除对应 Redis key</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private static final String KEY_PREFIX = "data:scope:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final SysRoleMapper roleMapper;
    private final SysUserDeptMapper userDeptMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 登录成功后调用：计算并缓存 DataScopeContext。
     */
    public void buildAndCache(SysUser user) {
        try {
            Map<String, Object> ctx = compute(user);
            String key = KEY_PREFIX + user.getTenantId() + ":" + user.getId();
            String json = objectMapper.writeValueAsString(ctx);
            stringRedisTemplate.opsForValue().set(key, json, TTL);
            log.debug("DataScopeService: cached scope={} for userId={}", ctx.get("dataScope"), user.getId());
        } catch (JsonProcessingException e) {
            log.error("DataScopeService: failed to serialize DataScopeContext for userId={}", user.getId(), e);
        }
    }

    /**
     * 角色或部门变更时，删除用户的数据权限缓存，下次请求时重建。
     */
    public void evict(String tenantId, Long userId) {
        String key = KEY_PREFIX + tenantId + ":" + userId;
        stringRedisTemplate.delete(key);
        log.info("DataScopeService: evicted cache for userId={}", userId);
    }

    // ---- private ----

    private Map<String, Object> compute(SysUser user) {
        Long userId = user.getId();

        // 1. 查用户所有有效角色，取最小 dataScope（1=最大权限）
        List<SysRole> roles = roleMapper.findRolesByUserId(userId);
        int minScope = roles.stream()
                .filter(r -> r.getDataScope() != null)
                .mapToInt(SysRole::getDataScope)
                .min()
                .orElse(1);

        // 2. 查主部门
        Long primaryDeptId = userDeptMapper.findPrimaryDeptId(userId);
        // 兜底：从 SysUser.deptId 冗余字段取
        if (primaryDeptId == null) {
            primaryDeptId = user.getDeptId();
        }

        // 3. 计算有效部门集合
        Set<Long> deptIds = computeDeptIds(minScope, roles, primaryDeptId);

        // 4. 组装（用 Map 方便序列化为 DataScopeContext）
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("dataScope", minScope);
        ctx.put("userId", userId);
        ctx.put("primaryDeptId", primaryDeptId);
        ctx.put("deptIds", deptIds);
        return ctx;
    }

    private Set<Long> computeDeptIds(int scope, List<SysRole> roles, Long primaryDeptId) {
        return switch (scope) {
            case 2 -> primaryDeptId != null ? Set.of(primaryDeptId) : Collections.emptySet();
            case 3 -> primaryDeptId != null
                    ? new HashSet<>(deptMapper.findSelfAndDescendantIds(primaryDeptId))
                    : Collections.emptySet();
            case 5 -> {
                // 取所有 dataScope=5 角色关联的自定义部门合集
                Set<Long> ids = new HashSet<>();
                roles.stream()
                        .filter(r -> r.getDataScope() != null && r.getDataScope() == 5)
                        .forEach(r -> ids.addAll(roleDeptMapper.findDeptIdsByRoleId(r.getId())));
                yield ids;
            }
            default -> Collections.emptySet();
        };
    }
}
```

---

## Task 12: 登录流程接入 `DataScopeService`

**Files:**
- Modify: `erp-services/erp-system/src/main/java/com/erp/system/application/service/UserService.java`

**Step 1: 注入 `DataScopeService` 并在 `verifyUser` 成功后调用**

在 `UserService` 中新增 `DataScopeService` 字段，并在 `verifyUser` 返回前调用 `buildAndCache`。

找到：
```java
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
```

改为：
```java
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final DataScopeService dataScopeService;
```

找到 `verifyUser` 中：
```java
        if (user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        return user;
```

改为：
```java
        if (user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        // 登录成功后异步写入数据权限缓存
        dataScopeService.buildAndCache(user);
        return user;
```

---

## Task 13: 全量编译验证 + commit

**Step 1: 编译 erp-commons**

```bash
cd /home/lolo/javaproject/simple/erp-platform && mvn compile -DskipTests -pl erp-commons -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

**Step 2: 编译 erp-system**

```bash
cd /home/lolo/javaproject/simple/erp-platform && mvn compile -DskipTests -pl erp-services/erp-system -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

**Step 3: 全量编译**

```bash
cd /home/lolo/javaproject/simple/erp-platform && mvn compile -DskipTests 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

**Step 4: 提交**

```bash
git -C /home/lolo/javaproject/simple/erp-platform add \
  erp-commons/erp-common-mybatis/ \
  erp-commons/erp-common-core/src/main/java/com/erp/common/core/constant/HeaderConstants.java \
  erp-services/erp-system/

git -C /home/lolo/javaproject/simple/erp-platform commit -m "feat: 实现注解驱动数据权限（@DataScope + DataPermissionInterceptor）

erp-common-mybatis 新增 @DataScope 注解、DataScopeContext/Holder、
ErpDataPermissionHandler、DataScopeFilter；
erp-system 新增部门表 DDL、SysDept/UserDept/RoleDept 实体及 Mapper、
DataScopeService（计算并缓存 Redis）并接入登录流程。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```
