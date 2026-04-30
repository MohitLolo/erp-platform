# 接口级鉴权设计文档

**日期：** 2026-04-30  
**状态：** 已确认  
**作者：** erp

---

## 背景

ERP 微服务平台已完成：
- **认证**：Gateway 层 Sa-Token JWT 校验，`AuthGlobalFilter` 注入 `X-User-Id` / `X-Tenant-Id` / `X-User-Name` 到下游 Header
- **行级数据权限**：`DataScopeFilter` + `ErpDataPermissionHandler`（5 级 scope）

**缺失**：接口级鉴权——某个用户是否有权限调用某个接口/操作。

---

## 现状分析

| 项目 | 现状 |
|---|---|
| Sa-Token 模式 | `jwt-default`（完全无状态 JWT，无 Redis Session） |
| `StpInterfaceImpl` | 已在 `erp-system` 实现，查 DB 获取权限码 |
| `@SaCheckPermission` | 已在 `SysUserController` 使用，但仅限 erp-system |
| 业务服务鉴权 | ❌ erp-purchase、erp-sale 等无 `StpInterface`，无法使用 `@SaCheckPermission` |
| JWT Extra | 仅含 `tenantId`/`userName`/`tenantName`，无角色/权限信息 |
| 双重 checkLogin | ~~`SaReactorFilter` + `AuthGlobalFilter` 各调用一次~~ 已修复 |

---

## 设计目标

1. **JWT 管认证**（你是谁）——保持无状态验签
2. **Sa-Token Session 管鉴权**（你能干什么）——权限列表存 Redis，高并发下纯内存读取
3. **所有业务服务**统一接入 `@SaCheckPermission`，无需各自实现权限查询逻辑
4. **权限变更实时生效**——角色/权限调整后主动刷新 Session，无需等待 Token 过期

---

## 方案选型

### 方案 A：jwt-mixin + 共享 StpInterface（**选定**）

| 维度 | 说明 |
|---|---|
| 认证 | JWT 验签，无状态 |
| 鉴权数据 | Redis Session，登录时写入，变更时主动刷新 |
| 性能 | 纯 Redis 读取，无 RPC，无 DB，O(1) |
| 权限变更 | 实时生效（主动 evict/refresh） |
| 接入成本 | 业务服务零改动，加注解即可 |

### 方案 B：jwt-default + Feign 远程查权限（弃用）

弃用原因：首次请求走 RPC 有网络开销；本地缓存导致权限变更最多延迟 5 分钟；引入服务间调用依赖。

### 方案 C：Gateway 集中鉴权（弃用）

弃用原因：Gateway 变业务瓶颈；粒度只能到路由级，无法精确到方法级；维护成本高。

---

## 详细设计

### 1. Sa-Token 模式切换

**所有环境 configmap**（`deploy/configmap/{env}/auth.yml`、`gateway.yml` 等）统一修改：

```yaml
sa-token:
  token-style: jwt-mixin   # 原：jwt-default
```

`jwt-mixin` 模式：JWT 负责认证验签（无状态），Redis Session 负责存储鉴权数据（有状态）。两者共存，互不干扰。

---

### 2. 登录写入权限 Session

**文件：** `erp-services/erp-auth/src/main/java/com/erp/auth/service/AuthService.java`

```java
// 登录
StpUtil.login(userId, SaLoginModel.create()
        .setExtra("tenantId", request.getTenantId())
        .setExtra("userName", userInfo.getUsername())
        .setExtra("tenantName", userInfo.getTenantName())
);

// 登录后立即写权限到 Redis Session（只查 permType=2 按钮权限、permType=3 接口权限）
SaSession session = StpUtil.getSessionByLoginId(userId);
List<String> permCodes = permissionMapper.findPermCodesByUserId(userId);
List<String> roleCodes = permissionMapper.findRoleCodesByUserId(userId);
session.set("permissions", permCodes);
session.set("roles", roleCodes);
```

**Redis Session 结构：**

```
Key:   satoken:login:session:{userId}
TTL:   与 sa-token.timeout 一致（86400s）
Value:
  permissions → ["system:user:list", "purchase:order:create", "sale:order:list", ...]
  roles       → ["admin", "purchase_manager"]
```

> 说明：`SysPermissionMapper.findPermCodesByUserId` 已存在，只需补充过滤条件 `AND p.perm_type IN (2, 3)`，排除菜单权限（`permType=1`），减少 Session 体积。

---

### 3. StpInterfaceImpl 迁移至 erp-common-auth

**原位置：** `erp-services/erp-system/src/main/java/com/erp/system/config/StpInterfaceImpl.java`（查 DB）

**新位置：** `erp-commons/erp-common-auth/src/main/java/com/erp/common/auth/config/StpInterfaceImpl.java`（读 Redis Session）

```java
package com.erp.common.auth.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限接口实现
 *
 * <p>从 Redis Session 读取权限列表，Session 在登录时由 erp-auth 写入。
 * 注册为 Spring Bean 后，所有服务的 @SaCheckPermission / @SaCheckRole 自动生效。
 *
 * @see com.erp.auth.service.AuthService#login
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) return List.of();
        List<String> perms = session.get("permissions");
        return perms != null ? perms : List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) return List.of();
        List<String> roles = session.get("roles");
        return roles != null ? roles : List.of();
    }
}
```

**同步操作：** 删除 `erp-system` 中的旧 `StpInterfaceImpl`，避免 Bean 冲突。

**自动注册：** `erp-common-auth` 已通过 `AutoConfiguration.imports` 自动装配，所有引入该模块的服务无需额外配置。

---

### 4. 权限变更缓存刷新

角色/权限变更后主动刷新 Session，无需等待 Token 过期。

**文件：** `erp-services/erp-system/src/main/java/com/erp/system/application/service/` 新增 `PermissionCacheService.java`

```java
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final SysPermissionMapper permissionMapper;

    /**
     * 刷新指定用户的权限 Session（角色/权限变更后调用）
     */
    public void refresh(Long userId) {
        SaSession session = StpUtil.getSessionByLoginId(userId, false);
        if (session == null) return;  // 用户未登录，无需刷新

        List<String> permCodes = permissionMapper.findPermCodesByUserId(userId);
        List<String> roleCodes = permissionMapper.findRoleCodesByUserId(userId);
        session.set("permissions", permCodes);
        session.set("roles", roleCodes);
    }

    /**
     * 踢出用户（禁用账号时调用）
     */
    public void kickOut(Long userId) {
        StpUtil.logout(userId);
    }
}
```

**触发时机：**

| 操作 | 调用方法 |
|---|---|
| 给用户分配/移除角色 | `permissionCacheService.refresh(userId)` |
| 修改角色的权限集合 | 查出该角色所有用户，批量 `refresh(userId)` |
| 禁用用户账号 | `permissionCacheService.kickOut(userId)` |

---

### 5. 业务服务接入方式

业务服务**只需加注解**，无任何其他改动：

```java
// erp-purchase — PurchaseOrderController.java
@PostMapping
@SaCheckPermission("purchase:order:create")
public R<Void> create(@RequestBody PurchaseOrderDTO dto) { ... }

@GetMapping("/list")
@SaCheckPermission("purchase:order:list")
public R<Page<PurchaseOrder>> list(...) { ... }

// erp-sale — SaleOrderController.java
@PostMapping
@SaCheckPermission("sale:order:create")
public R<Void> create(@RequestBody SaleOrderDTO dto) { ... }
```

**权限码命名规范：** `{模块}:{资源}:{操作}`，与 `sys_permission.perm_code` 字段保持一致。

| 模块 | 示例权限码 |
|---|---|
| system | `system:user:list`、`system:role:edit` |
| purchase | `purchase:order:create`、`purchase:order:approve` |
| sale | `sale:order:list`、`sale:order:cancel` |
| inventory | `inventory:stock:adjust`、`inventory:stock:query` |

---

### 6. Gateway 层职责边界

| 层 | 职责 | 不做什么 |
|---|---|---|
| Gateway `SaReactorFilter` | JWT 验签，认证（checkLogin） | 不做业务权限校验 |
| Gateway `AuthGlobalFilter` | 解析 JWT Extra → 注入下游 Header | 不调用 checkLogin（已修复） |
| 业务服务 `@SaCheckPermission` | 接口级鉴权，读 Redis Session | 不查 DB，不调 RPC |

Gateway 保持轻量，业务权限逻辑内聚在各服务，职责清晰。

---

## 依赖关系确认

```
所有业务服务（erp-purchase / erp-sale / erp-inventory ...）
  └── erp-common-auth
        ├── sa-token-spring-boot3-starter  ✅
        ├── sa-token-jwt                   ✅
        └── sa-token-redis-jackson         ✅（Session 读写必须）
  └── erp-common-redis
        └── spring-boot-starter-data-redis ✅（连接同一 Redis）
```

所有依赖已就绪，无需新增任何 Maven 依赖。

---

## 改动范围汇总

| 文件 | 操作 | 说明 |
|---|---|---|
| `deploy/configmap/{env}/auth.yml` | 修改 | `jwt-default` → `jwt-mixin`（所有环境） |
| `deploy/configmap/{env}/gateway.yml` | 修改 | 同上（如果 gateway 有独立配置） |
| `erp-auth/AuthService.java` | 修改 | 登录后写权限到 Redis Session |
| `erp-system/StpInterfaceImpl.java` | 删除 | 迁移到 erp-common-auth |
| `erp-common-auth/StpInterfaceImpl.java` | 新增 | 读 Redis Session |
| `erp-system/PermissionCacheService.java` | 新增 | 权限变更刷新逻辑 |
| `erp-system/SysPermissionMapper.java` | 修改 | `findPermCodesByUserId` 加 `permType IN (2,3)` 过滤 |
| 各业务服务 Controller | 修改 | 添加 `@SaCheckPermission` 注解 |

---

## 注意事项

1. **`getSessionByLoginId(loginId, false)` 第二个参数**：`false` 表示 Session 不存在时不自动创建，避免未登录用户产生无效 Session
2. **`jwt-mixin` 滚动升级**：切换 token-style 后旧的 `jwt-default` Token 会失效，需要通知用户重新登录（或在维护窗口期发布）
3. **`sys_permission` 初始数据**：业务模块权限码需要在 `sys_permission` 表中有对应记录，`@SaCheckPermission` 注解的字符串要与 `perm_code` 字段完全一致
4. **超级管理员**：Sa-Token 支持 `StpUtil.isAdmin()` 或在 `getPermissionList` 返回 `["*"]` 实现超管跳过权限校验
