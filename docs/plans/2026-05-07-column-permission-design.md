# 列级数据权限设计文档

**日期：** 2026-05-07
**状态：** 已确认，待实施
**关联计划：** 2026-04-30-authorization-design.md（接口级鉴权）、2026-04-30-data-permission-design.md（行级数据权限）

---

## 背景

系统已实现：
- **接口级鉴权**：Sa-Token jwt-mixin + `@SaCheckPermission`，权限码登录时写入 Redis Session
- **行级数据权限**：`@DataScope` + MyBatis-Plus `DataPermissionInterceptor`，SQL 层过滤行

本文档设计**列级数据权限**：同样的查询接口，不同角色能看到不同的字段列。无权限的字段在 JSON 响应中保留 key 但值置为 `null`。

---

## 核心决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 管理粒度 | 角色级别 | 简单，够用，与现有角色体系一致 |
| 拦截层 | Jackson 序列化层 | SQL 不变，业务逻辑（计算、聚合）不受影响 |
| 权限标识 | 注解 + 复用 `sys_permission.perm_code` | 零新表，统一权限管理入口 |
| 无权限字段处理 | 保留 key，值置为 `null` | 前端可统一用 null 渲染占位符 |

---

## 数据库变更

### 1. `sys_permission` 新增字段

```sql
ALTER TABLE sys_permission
    ADD COLUMN field_name VARCHAR(128) NULL
    COMMENT '字段名（perm_type=4 列权限专用，对应 VO 中 @ColumnPermission 注解所保护的字段）';
```

### 2. `perm_type` 枚举扩展

| 值 | 含义 | 专用字段 |
|----|------|---------|
| 1 | 菜单 | `route_path`, `component`, `icon` |
| 2 | 按钮 | — |
| 3 | 接口 | `api_path`, `api_method` |
| **4** | **列权限** | **`field_name`** |

### 3. `findPermCodesByUserId` 查询范围扩展

`SysPermissionMapper.findPermCodesByUserId` 的过滤条件由 `IN (2,3)` 改为 `IN (2,3,4)`，使列权限码随登录一起写入 Redis Session，**无额外 Redis 查询开销**。

---

## 架构设计

### 数据流

```
登录
  └─ AuthService
       └─ Redis Session: permissions = [..., "purchase:order:view_cost", ...]
            （perm_type=4 的权限码与按钮/接口权限码统一存储）

请求进入
  └─ DataScopeFilter（已有，微改）
       ├─ 原有：DataScopeContext → DataScopeContextHolder（行权限）
       └─ 新增：Session.permissions → ColumnPermissionContextHolder（列权限，TTL Set<String>）

Controller → Service → Mapper
  └─ SQL 完全不变，返回全量 VO

Jackson 序列化
  └─ ColumnPermissionBeanSerializerModifier（全局，自动生效）
       └─ 逐字段检查 @ColumnPermission
            ├─ 无注解                          → 正常输出
            ├─ 有注解 + permissions 包含权限码  → 正常输出
            └─ 有注解 + permissions 不含权限码  → 输出 null

请求结束
  └─ DataScopeFilter finally
       ├─ DataScopeContextHolder.clear()
       └─ ColumnPermissionContextHolder.clear()
```

---

## 新增组件（全部位于 `erp-common-web`）

### ① `@ColumnPermission` 注解

```
位置：erp-commons/erp-common-web/src/main/java/com/erp/common/web/annotation/ColumnPermission.java
```

标注在 VO 字段上，声明查看该字段所需的权限码（对应 `sys_permission.perm_code`，`perm_type=4`）。

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ColumnPermission {
    /** 对应 sys_permission.perm_code（perm_type=4） */
    String value();
}
```

### ② `ColumnPermissionContextHolder`

```
位置：erp-commons/erp-common-web/src/main/java/com/erp/common/web/column/ColumnPermissionContextHolder.java
```

基于 `TransmittableThreadLocal`，生命周期与 HTTP 请求一致。存储当前用户拥有的列权限码 `Set<String>`，Jackson 序列化时 `O(1)` 查找。

### ③ `ColumnPermissionSerializer`

```
位置：erp-commons/erp-common-web/src/main/java/com/erp/common/web/column/ColumnPermissionSerializer.java
```

包装原始 `JsonSerializer`：

- `ColumnPermissionContextHolder` 为 null（内部 Feign 调用）→ 直接委托原始 Serializer，**不过滤**
- Holder 不为 null 且包含权限码 → 委托原始 Serializer 正常输出
- Holder 不为 null 且不含权限码 → 写 `null`

### ④ `ColumnPermissionBeanSerializerModifier`

```
位置：erp-commons/erp-common-web/src/main/java/com/erp/common/web/column/ColumnPermissionBeanSerializerModifier.java
```

实现 Jackson `BeanSerializerModifier`，在 Jackson 构建 Bean Serializer 时介入：对标有 `@ColumnPermission` 的字段，将其原始 Serializer 替换为 `ColumnPermissionSerializer` 包装版。全局注册，对所有 VO/Entity 自动生效，包括嵌套对象。

### ⑤ `ColumnPermissionJacksonConfig`

```
位置：erp-commons/erp-common-web/src/main/java/com/erp/common/web/column/ColumnPermissionJacksonConfig.java
```

`@Configuration` 类，将 `ColumnPermissionBeanSerializerModifier` 注册进 Spring 托管的 `ObjectMapper`。

---

## 已有组件改动

| 组件 | 位置 | 改动内容 | 改动量 |
|------|------|---------|--------|
| `DataScopeFilter` | `erp-common-mybatis` | 请求开始时从 Sa-Token Session 读 `permissions` 写入 `ColumnPermissionContextHolder`；`finally` 块追加 `ColumnPermissionContextHolder.clear()` | +10 行 |
| `SysPermissionMapper` | `erp-system` | `IN (2,3)` → `IN (2,3,4)` | +2 字符 |
| `SysPermission` entity | `erp-system` | 新增 `fieldName` 字段 | +5 行 |
| `AutoConfiguration.imports` | `erp-common-web` | 追加 `ColumnPermissionJacksonConfig` | +1 行 |

---

## 业务服务接入方式

以采购订单为例，**三步完成接入，不改 Service / Mapper / SQL**：

### Step 1：VO 字段加注解

```java
public class PurchaseOrderVO {

    private String orderNo;          // 无注解，所有人可见

    @ColumnPermission("purchase:order:view_cost")
    private BigDecimal unitCost;     // 无权限时响应 null

    @ColumnPermission("purchase:order:view_profit")
    private BigDecimal profit;       // 无权限时响应 null
}
```

### Step 2：`sys_permission` 插入权限记录

```sql
INSERT INTO sys_permission (tenant_id, parent_id, perm_name, perm_code,
    perm_type, field_name, sort_order, status, create_time, update_time, deleted)
VALUES
    ('default', #{采购订单菜单id}, '查看采购成本价',
     'purchase:order:view_cost',   4, 'unitCost', 1, 1, NOW(), NOW(), 0),
    ('default', #{采购订单菜单id}, '查看采购利润',
     'purchase:order:view_profit', 4, 'profit',   2, 1, NOW(), NOW(), 0);
```

### Step 3：角色管理界面分配权限

在系统管理 → 角色管理中，为对应角色勾选上述权限即可，无需重启服务。

---

## 边界情况

| 场景 | 行为 |
|------|------|
| 未登录请求 | Gateway 拦截返回 401，不触发序列化 |
| 内部 Feign 调用（`X-Inner-Call: true`） | `ColumnPermissionContextHolder` 为 null，Serializer 直接放行，输出真实值 |
| 字段本身值为 null | 无论有无权限，Jackson 按 `@JsonInclude` 正常处理 |
| `List<VO>` / `Page<VO>` 返回多条记录 | Jackson 逐条序列化，自动生效 |
| 嵌套 VO | `BeanSerializerModifier` 对所有 Bean 类型生效，嵌套字段同样检查 |
| 权限变更（角色重新分配权限） | 调用 `PermissionCacheService.refresh(userId)` 刷新 Redis Session，**实时生效** |

---

## 与现有权限体系的关系

```
sys_permission
  ├── perm_type=1  菜单    → 前端路由渲染
  ├── perm_type=2  按钮    → @SaCheckPermission 接口级鉴权（Redis Session）
  ├── perm_type=3  接口    → @SaCheckPermission 接口级鉴权（Redis Session）
  └── perm_type=4  列权限  → @ColumnPermission Jackson 序列化过滤（Redis Session）
                                                      ↑
                              同一个 permissions Set，登录时统一写入，无额外开销
```

所有权限类型统一在 `sys_permission` 表管理，统一在角色管理界面分配，统一通过 Redis Session 下发，**一套体系，四种粒度**。

---

## 不在本次范围内

- 列权限的管理界面（前端）
- 用户级别的列权限覆盖（当前仅角色级别）
- 字段脱敏（如显示 `***`，当前统一为 `null`）
