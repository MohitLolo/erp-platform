# 接口级鉴权 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 基于 Sa-Token jwt-mixin 模式实现接口级鉴权，权限列表登录时写入 Redis Session，所有业务服务通过 `@SaCheckPermission` 注解统一鉴权，无 DB/RPC 开销。

**Architecture:** 切换 Sa-Token 为 jwt-mixin 模式，JWT 负责认证验签（无状态），Redis Session 负责存储每个用户的权限码列表（有状态）。`StpInterfaceImpl` 从 `erp-system`（查 DB）迁移到 `erp-common-auth`（读 Redis Session），所有业务服务自动继承鉴权能力。

**Tech Stack:** Sa-Token 1.40.0 jwt-mixin、Spring Boot 3.2.5、Redis（sa-token-redis-jackson）、Spring AOP（@SaCheckPermission）

---

## 前置知识

- **jwt-mixin 模式**：Token 本身是 JWT（可验签，无状态），但同时维护 Redis Session（`satoken:login:session:{userId}`），可在 Session 里存任意数据。每次请求：先验 JWT 签名 → 再读 Redis Session 取权限。
- **StpInterface**：Sa-Token 在执行 `@SaCheckPermission` 时调用 `StpInterface.getPermissionList()`，我们让它从 Redis Session 读，而不是查 DB。
- **AutoConfiguration.imports**：Spring Boot 3 的自动配置入口文件，放在 `META-INF/spring/` 下，让 `erp-common-auth` 的 Bean（`StpInterfaceImpl`）自动注册到所有引入该模块的服务中。

---

## Task 1：切换 Sa-Token 模式为 jwt-mixin（所有环境 configmap）

**Files:**
- Modify: `deploy/configmap/dev/auth.yml`
- Modify: `deploy/configmap/test/auth.yml`
- Modify: `deploy/configmap/prod/auth.yml`

### Step 1：修改 dev 环境

编辑 `deploy/configmap/dev/auth.yml`，将 `token-style: jwt-default` 改为 `token-style: jwt-mixin`：

```yaml
sa-token:
  token-name: Authorization
  token-style: jwt-mixin        # 原：jwt-default
  timeout: 86400
  active-timeout: 1800
  is-concurrent: false
  is-share: true
  is-read-header: true
  is-read-cookie: false
  jwt-secret-key: ${SA_TOKEN_JWT_SECRET:erp-platform-secret-key-2024}
```

### Step 2：同样修改 test 和 prod 环境

`deploy/configmap/test/auth.yml` 和 `deploy/configmap/prod/auth.yml` 做同样的 `jwt-default` → `jwt-mixin` 替换。

### Step 3：验证

```bash
grep -r "token-style" deploy/configmap/
```

期望输出（三个文件全部是 jwt-mixin）：
```
deploy/configmap/dev/auth.yml:  token-style: jwt-mixin
deploy/configmap/test/auth.yml:  token-style: jwt-mixin
deploy/configmap/prod/auth.yml:  token-style: jwt-mixin
```

### Step 4：提交

```bash
git add deploy/configmap/dev/auth.yml deploy/configmap/test/auth.yml deploy/configmap/prod/auth.yml
git commit -m "config: switch sa-token token-style from jwt-default to jwt-mixin"
```

---

## Task 2：修改 SysPermissionMapper — 过滤菜单权限

**Files:**
- Modify: `erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysPermissionMapper.java`

### Step 1：了解现状

`findPermCodesByUserId` 当前查询所有权限码，包括 `perm_type=1`（菜单），菜单权限不需要放入 Session。`sys_permission.perm_type`：1=菜单，2=按钮，3=接口。

### Step 2：修改 findPermCodesByUserId，加 perm_type 过滤

将：
```java
@Select("""
        SELECT DISTINCT p.perm_code
        FROM sys_permission p
        INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
        INNER JOIN sys_role r ON r.id = rp.role_id
        INNER JOIN sys_user_role ur ON ur.role_id = r.id
        WHERE ur.user_id = #{userId}
          AND r.status = 1
          AND p.status = 1
          AND p.deleted = 0
        """)
List<String> findPermCodesByUserId(@Param("userId") Long userId);
```

改为：
```java
@Select("""
        SELECT DISTINCT p.perm_code
        FROM sys_permission p
        INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
        INNER JOIN sys_role r ON r.id = rp.role_id
        INNER JOIN sys_user_role ur ON ur.role_id = r.id
        WHERE ur.user_id = #{userId}
          AND r.status = 1
          AND p.status = 1
          AND p.deleted = 0
          AND p.perm_type IN (2, 3)
        """)
List<String> findPermCodesByUserId(@Param("userId") Long userId);
```

### Step 3：提交

```bash
git add erp-services/erp-system/src/main/java/com/erp/system/infrastructure/mapper/SysPermissionMapper.java
git commit -m "feat(system): filter perm_type IN (2,3) in findPermCodesByUserId, exclude menu perms"
```

---

## Task 3：新建 erp-common-auth 的 StpInterfaceImpl（读 Redis Session）

**Files:**
- Create: `erp-commons/erp-common-auth/src/main/java/com/erp/common/auth/config/StpInterfaceImpl.java`
- Create: `erp-commons/erp-common-auth/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Step 1：创建包目录（命令确认）

```bash
mkdir -p erp-commons/erp-common-auth/src/main/java/com/erp/common/auth/config
mkdir -p erp-commons/erp-common-auth/src/main/resources/META-INF/spring
```

### Step 2：创建 StpInterfaceImpl.java

创建文件 `erp-commons/erp-common-auth/src/main/java/com/erp/common/auth/config/StpInterfaceImpl.java`：

```java
package com.erp.common.auth.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限接口实现（共享版）
 *
 * <p>从 Redis Session 读取权限列表，Session 在登录时由 erp-auth 写入。
 * 注册为 Spring Bean 后，所有引入 erp-common-auth 的服务中
 * {@code @SaCheckPermission} / {@code @SaCheckRole} 自动生效。
 *
 * <p>依赖 jwt-mixin 模式，不支持 jwt-default（无 Session）。
 *
 * @see com.erp.auth.service.AuthService
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return List.of();
        }
        List<String> perms = session.get("permissions");
        return perms != null ? perms : List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return List.of();
        }
        List<String> roles = session.get("roles");
        return roles != null ? roles : List.of();
    }
}
```

> **说明：** `getSessionByLoginId(loginId, false)` 第二个参数 `false` 表示 Session 不存在时不自动创建，避免未登录用户在 Redis 里产生空 Session。

### Step 3：创建 AutoConfiguration.imports

创建文件 `erp-commons/erp-common-auth/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
com.erp.common.auth.config.StpInterfaceImpl
```

> **说明：** Spring Boot 3 通过此文件自动注册类为 Bean，无需在每个业务服务里手动 `@ComponentScan`。

### Step 4：验证文件结构

```bash
find erp-commons/erp-common-auth/src -type f | sort
```

期望输出：
```
erp-commons/erp-common-auth/src/main/java/com/erp/common/auth/config/StpInterfaceImpl.java
erp-commons/erp-common-auth/src/main/java/com/erp/common/auth/util/JwtUtil.java
erp-commons/erp-common-auth/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### Step 5：提交

```bash
git add erp-commons/erp-common-auth/src/
git commit -m "feat(common-auth): add StpInterfaceImpl reading from Redis Session + AutoConfiguration.imports"
```

---

## Task 4：删除 erp-system 旧 StpInterfaceImpl（避免 Bean 冲突）

**Files:**
- Delete: `erp-services/erp-system/src/main/java/com/erp/system/config/StpInterfaceImpl.java`

### Step 1：确认旧文件位置

```bash
find erp-services/erp-system -name "StpInterfaceImpl.java"
```

期望输出：
```
erp-services/erp-system/src/main/java/com/erp/system/config/StpInterfaceImpl.java
```

### Step 2：删除文件

```bash
rm erp-services/erp-system/src/main/java/com/erp/system/config/StpInterfaceImpl.java
```

### Step 3：检查是否有其他地方引用了旧的 StpInterfaceImpl

```bash
grep -r "StpInterfaceImpl" erp-services/erp-system/src/
```

期望输出：无任何结果（文件已删除，无其他引用）。

### Step 4：提交

```bash
git add -u erp-services/erp-system/src/main/java/com/erp/system/config/StpInterfaceImpl.java
git commit -m "refactor(system): remove StpInterfaceImpl, migrated to erp-common-auth"
```

---

## Task 5：修改 AuthService — 登录后写权限到 Redis Session

**Files:**
- Modify: `erp-services/erp-auth/src/main/java/com/erp/auth/service/AuthService.java`

### Step 1：了解现状

`AuthService.login()` 当前：
1. 调 `systemUserFeign.verifyUser()` 拿到 `userInfo`（含 userId）
2. 调 `StpUtil.login(userId, ...)` 完成认证
3. 返回 token

**目标：** 在 Step 2 和 Step 3 之间，写权限到 Redis Session。

`erp-auth` 没有 DB 访问，不能直接查 `sys_permission`。需要通过 Feign 从 `erp-system` 拿权限列表，`erp-system` 的 `UserService` 已有 `getUserPermissions(userId)` 和 `getUserRoles(userId)` 方法，只需在 `SystemUserFeign` 里加对应接口。

### Step 2：查看 SystemUserFeign 现有接口

```bash
cat erp-services/erp-auth/src/main/java/com/erp/auth/infrastructure/feign/SystemUserFeign.java
```

确认现有方法后，新增权限/角色查询方法：

```java
/**
 * 查询用户权限码列表（permType=2,3）
 */
@GetMapping("/internal/users/{userId}/permissions")
List<String> getUserPermissions(@PathVariable("userId") Long userId);

/**
 * 查询用户角色码列表
 */
@GetMapping("/internal/users/{userId}/roles")
List<String> getUserRoles(@PathVariable("userId") Long userId);
```

### Step 3：在 erp-system 新增内部接口 UserInternalController

创建文件 `erp-services/erp-system/src/main/java/com/erp/system/controller/UserInternalController.java`：

```java
package com.erp.system.controller;

import com.erp.system.application.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部服务调用接口（不对外暴露，仅供 erp-auth 通过 Feign 调用）
 *
 * <p>此接口不加 @SaCheckPermission，由 Gateway 路由隔离保证安全。
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    /**
     * 查询用户权限码列表（permType=2,3，排除菜单权限）
     */
    @GetMapping("/{userId}/permissions")
    public List<String> getUserPermissions(@PathVariable Long userId) {
        return userService.getUserPermissions(userId);
    }

    /**
     * 查询用户角色码列表
     */
    @GetMapping("/{userId}/roles")
    public List<String> getUserRoles(@PathVariable Long userId) {
        return userService.getUserRoles(userId);
    }
}
```

> **安全说明：** `/internal/**` 路径应在 Gateway 路由配置中不对外暴露（仅允许内部 K8s Service DNS 调用）。如 Gateway 路由只暴露 `/api/**`，则 `/internal/**` 天然隔离。

### Step 4：修改 AuthService.login()

将 `AuthService.java` 的 `login()` 方法改为：

```java
public LoginResponse login(LoginRequest request) {
    // 1. 调用 system 服务验证用户名密码
    String passwordMd5 = DigestUtils.md5DigestAsHex(request.getPassword().getBytes());
    SystemUserFeign.UserInfo userInfo = systemUserFeign.verifyUser(
            request.getTenantId(),
            request.getUsername(),
            passwordMd5
    );

    if (userInfo == null) {
        throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
    }
    if (!userInfo.isEnabled()) {
        throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号已被禁用");
    }

    // 2. Sa-Token 登录，写入 JWT Extra 信息
    StpUtil.login(userInfo.getUserId(),
            SaLoginModel.create()
                    .setExtra("tenantId", request.getTenantId())
                    .setExtra("userName", userInfo.getUsername())
                    .setExtra("tenantName", userInfo.getTenantName())
    );

    // 3. 写权限到 Redis Session（jwt-mixin 模式下 Session 已创建）
    SaSession session = StpUtil.getSessionByLoginId(userInfo.getUserId());
    List<String> permCodes = systemUserFeign.getUserPermissions(userInfo.getUserId());
    List<String> roleCodes = systemUserFeign.getUserRoles(userInfo.getUserId());
    session.set("permissions", permCodes != null ? permCodes : List.of());
    session.set("roles", roleCodes != null ? roleCodes : List.of());

    String token = StpUtil.getTokenValue();
    log.info("User login success: userId={}, tenantId={}, permissions={}",
            userInfo.getUserId(), request.getTenantId(), permCodes != null ? permCodes.size() : 0);

    return LoginResponse.builder()
            .token(token)
            .tokenType("Bearer")
            .expiresIn(StpUtil.getTokenTimeout())
            .userId(userInfo.getUserId())
            .username(userInfo.getUsername())
            .tenantId(request.getTenantId())
            .tenantName(userInfo.getTenantName())
            .build();
}
```

需要新增 import：
```java
import cn.dev33.satoken.session.SaSession;
import java.util.List;
```

### Step 5：提交

```bash
git add erp-services/erp-auth/src/main/java/com/erp/auth/infrastructure/feign/SystemUserFeign.java
git add erp-services/erp-auth/src/main/java/com/erp/auth/service/AuthService.java
git add erp-services/erp-system/src/main/java/com/erp/system/controller/UserInternalController.java
git commit -m "feat(auth): write permission/role list to Sa-Token Redis Session on login"
```

---

## Task 6：新增 PermissionCacheService — 权限变更实时刷新

**Files:**
- Create: `erp-services/erp-system/src/main/java/com/erp/system/application/service/PermissionCacheService.java`

### Step 1：创建 PermissionCacheService.java

```java
package com.erp.system.application.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.erp.system.infrastructure.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限缓存刷新服务
 *
 * <p>在角色/权限变更后主动刷新用户的 Sa-Token Redis Session，
 * 使权限变更实时生效，无需等待 Token 过期。
 *
 * <p>调用时机：
 * <ul>
 *   <li>给用户分配/移除角色</li>
 *   <li>修改角色的权限集合（需批量刷新该角色下所有用户）</li>
 *   <li>禁用用户账号（调用 kickOut）</li>
 * </ul>
 *
 * @author erp
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final SysPermissionMapper permissionMapper;

    /**
     * 刷新指定用户的权限 Session
     *
     * <p>如果用户未登录（Session 不存在），则跳过，不产生空 Session。
     *
     * @param userId 用户 ID
     */
    public void refresh(Long userId) {
        SaSession session = StpUtil.getSessionByLoginId(userId, false);
        if (session == null) {
            log.debug("User {} is not logged in, skip permission cache refresh", userId);
            return;
        }
        List<String> permCodes = permissionMapper.findPermCodesByUserId(userId);
        List<String> roleCodes = permissionMapper.findRoleCodesByUserId(userId);
        session.set("permissions", permCodes);
        session.set("roles", roleCodes);
        log.info("Permission cache refreshed for userId={}, permCount={}", userId, permCodes.size());
    }

    /**
     * 踢出用户（清除 Token + Session）
     *
     * <p>用于禁用账号、强制下线等场景。
     *
     * @param userId 用户 ID
     */
    public void kickOut(Long userId) {
        StpUtil.logout(userId);
        log.info("User kicked out: userId={}", userId);
    }
}
```

### Step 2：验证文件创建成功

```bash
find erp-services/erp-system/src/main/java/com/erp/system/application/service/ -name "*.java"
```

期望输出（三个文件）：
```
.../DataScopeService.java
.../PermissionCacheService.java
.../UserService.java
```

### Step 3：提交

```bash
git add erp-services/erp-system/src/main/java/com/erp/system/application/service/PermissionCacheService.java
git commit -m "feat(system): add PermissionCacheService for real-time permission refresh on role change"
```

---

## Task 7：示范业务服务接入 — PurchaseOrderController

**Files:**
- Create: `erp-services/erp-purchase/src/main/java/com/erp/purchase/controller/PurchaseOrderController.java`

### Step 1：创建 PurchaseOrderController.java

controller 目录已存在（但为空）。创建以下文件作为接入示范，同时涵盖常见 CRUD 权限码：

```java
package com.erp.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.erp.common.core.response.R;
import com.erp.purchase.domain.entity.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购订单接口
 *
 * <p>权限码规范：{模块}:{资源}:{操作}
 * 对应 sys_permission.perm_code，需在数据库中有对应记录。
 */
@Slf4j
@RestController
@RequestMapping("/purchase/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    // TODO: 注入 PurchaseOrderService（待实现）

    /**
     * 查询采购订单列表
     */
    @GetMapping("/list")
    @SaCheckPermission("purchase:order:list")
    public R<List<PurchaseOrder>> list() {
        // TODO: 调用 service
        return R.ok(List.of());
    }

    /**
     * 查询采购订单详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("purchase:order:query")
    public R<PurchaseOrder> getById(@PathVariable Long id) {
        // TODO: 调用 service
        return R.ok(null);
    }

    /**
     * 新建采购订单（草稿）
     */
    @PostMapping
    @SaCheckPermission("purchase:order:create")
    public R<Void> create(@RequestBody PurchaseOrder order) {
        // TODO: 调用 service
        return R.ok();
    }

    /**
     * 确认采购订单
     */
    @PutMapping("/{id}/confirm")
    @SaCheckPermission("purchase:order:confirm")
    public R<Void> confirm(@PathVariable Long id) {
        // TODO: 调用 service
        return R.ok();
    }

    /**
     * 取消采购订单
     */
    @PutMapping("/{id}/cancel")
    @SaCheckPermission("purchase:order:cancel")
    public R<Void> cancel(@PathVariable Long id) {
        // TODO: 调用 service
        return R.ok();
    }
}
```

### Step 2：确认 R 类的 ok() 无参方法存在

```bash
grep -n "public static.*ok()" erp-commons/erp-common-core/src/main/java/com/erp/common/core/response/R.java
```

若无无参 `ok()` 方法，改用 `R.ok(null)` 或 `R.<Void>ok()`。

### Step 3：提交

```bash
git add erp-services/erp-purchase/src/main/java/com/erp/purchase/controller/PurchaseOrderController.java
git commit -m "feat(purchase): add PurchaseOrderController with @SaCheckPermission annotations as reference"
```

---

## Task 8：验证整体链路可编译

### Step 1：编译 erp-commons

```bash
cd /home/lolo/javaproject/simple/erp-platform
mvn clean install -pl erp-commons/erp-common-auth -am -DskipTests -q
```

期望：`BUILD SUCCESS`

### Step 2：编译 erp-system

```bash
mvn clean install -pl erp-services/erp-system -am -DskipTests -q
```

期望：`BUILD SUCCESS`

### Step 3：编译 erp-auth

```bash
mvn clean install -pl erp-services/erp-auth -am -DskipTests -q
```

期望：`BUILD SUCCESS`

### Step 4：编译 erp-purchase

```bash
mvn clean install -pl erp-services/erp-purchase -am -DskipTests -q
```

期望：`BUILD SUCCESS`

### Step 5：全量编译验证

```bash
mvn clean compile -DskipTests -q
```

期望：`BUILD SUCCESS`，无任何编译错误。

---

## Task 9：补充 sys_permission 初始数据（SQL migration）

**Files:**
- Create: `erp-services/erp-system/src/main/resources/db/migration/V3__purchase_permissions.sql`

### Step 1：确认已有 migration 版本

```bash
ls erp-services/erp-system/src/main/resources/db/migration/
```

已知有 V1、V2，新建 V3。

### Step 2：创建 V3 migration 文件

```sql
-- =====================================================================
-- V3: 采购模块接口权限初始数据
-- 为 erp-purchase 的 @SaCheckPermission 注解提供对应权限记录
-- =====================================================================

INSERT INTO `sys_permission` (`tenant_id`, `parent_id`, `perm_name`, `perm_code`, `perm_type`,
                               `api_path`, `api_method`, `sort`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
    -- 采购订单菜单（permType=1，前端用，不进入鉴权 Session）
    ('default', 0,    '采购管理',     'purchase',              1, NULL,                    NULL,   1, 1, NOW(), NOW(), 0),
    ('default', NULL, '采购订单',     'purchase:order',         1, NULL,                    NULL,   1, 1, NOW(), NOW(), 0),
    -- 采购订单按钮/接口权限（permType=2 按钮，permType=3 接口）
    ('default', NULL, '采购订单列表', 'purchase:order:list',    2, '/purchase/orders/list', 'GET',  1, 1, NOW(), NOW(), 0),
    ('default', NULL, '采购订单详情', 'purchase:order:query',   2, '/purchase/orders/{id}', 'GET',  2, 1, NOW(), NOW(), 0),
    ('default', NULL, '新建采购订单', 'purchase:order:create',  2, '/purchase/orders',      'POST', 3, 1, NOW(), NOW(), 0),
    ('default', NULL, '确认采购订单', 'purchase:order:confirm', 2, '/purchase/orders/{id}/confirm', 'PUT', 4, 1, NOW(), NOW(), 0),
    ('default', NULL, '取消采购订单', 'purchase:order:cancel',  2, '/purchase/orders/{id}/cancel',  'PUT', 5, 1, NOW(), NOW(), 0);
```

> **说明：** `tenant_id='default'` 是系统内置租户，实际数据依项目的租户初始化逻辑调整。菜单项（`perm_type=1`）的 `parent_id` 需根据实际 `sys_permission` 表中的父节点 ID 填写；如不确定，先用 NULL，后期通过管理界面维护。

### Step 3：提交

```bash
git add erp-services/erp-system/src/main/resources/db/migration/V3__purchase_permissions.sql
git commit -m "feat(system): add V3 migration with purchase order permission records"
```

---

## 验收标准

完成所有 Task 后，以下行为必须符合预期：

| 场景 | 期望行为 |
|---|---|
| 用户登录 | Redis 中出现 `satoken:login:session:{userId}`，含 `permissions` 和 `roles` 字段 |
| 已登录用户访问有权限的接口 | 正常返回数据，HTTP 200 |
| 已登录用户访问无权限的接口 | Sa-Token 抛出 `NotPermissionException`，返回 403 |
| 未登录请求 | Gateway `SaReactorFilter` 拦截，返回 401 |
| 角色变更后调用 `PermissionCacheService.refresh(userId)` | Redis Session 的 `permissions` 立即更新 |
| 全量 Maven 编译 | `BUILD SUCCESS`，零错误 |

---

## 注意事项

1. **jwt-mixin 切换是破坏性变更**：现有的 `jwt-default` Token 在切换后会失效（两种模式的 Token 格式不同）。部署时需要维护窗口，或通知用户重新登录。

2. **`/internal/**` 路径安全**：`UserInternalController` 只应被 K8s 内部服务调用，不应通过 Gateway 对外暴露。若 Gateway 路由配置基于 `/api/**` 前缀，则 `/internal/**` 天然隔离；否则需在 Gateway 路由中明确排除。

3. **AutoConfiguration.imports 格式**：每行一个全限定类名，文件末尾必须有换行符，否则 Spring Boot 可能无法正确解析最后一行。

4. **Feign 调用 getUserPermissions 的超时**：登录时多一次 Feign 调用查权限，建议在 `SystemUserFeign` 上设置合理超时（如 3s），避免权限查询慢导致登录超时。
