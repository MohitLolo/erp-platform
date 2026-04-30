# 数据权限设计文档

**日期：** 2026-04-30
**状态：** 已确认，待实施

---

## 一、背景

ERP 系统需要对业务数据按用户权限范围进行行级过滤（Row-Level Security）。例如：
- 销售经理只能看本部门的销售订单
- 采购专员只能看自己创建的采购单
- 管理员可以看全公司数据

当前 `SysRole` 已有 `dataScope` 字段预定义了权限维度，但缺少部门表、用户多部门关联表，以及 MyBatis-Plus 层的拦截实现。

---

## 二、设计原则

1. **注解驱动，按需接入**：只有标注了 `@DataScope` 的 Mapper 方法才会注入 SQL 过滤条件，未标注的查询不受影响
2. **框架封装在 `erp-common-mybatis`**：业务服务零感知，只加注解即可
3. **权限数据缓存化**：各业务服务不直接查 `erp-system`，通过 Redis 缓存取当前用户的权限上下文
4. **多角色取最大权限**：同一用户有多个角色时，取 `dataScope` 值最小的（1 为最大权限）

---

## 三、数据权限维度（5 档）

| dataScope 值 | 含义 | SQL 过滤逻辑 |
|-------------|------|------------|
| 1 | 全部数据 | 不加任何过滤条件 |
| 2 | 本部门数据 | `dept_id IN (用户主部门ID)` |
| 3 | 本部门及下级 | `dept_id IN (主部门ID + 所有子孙部门ID)` |
| 4 | 仅本人数据 | `create_by = userId` |
| 5 | 自定义部门 | `dept_id IN (sys_role_dept 中该角色指定的部门列表)` |

---

## 四、新增表结构（erp-system 库）

### 4.1 部门表

```sql
CREATE TABLE sys_dept (
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
) COMMENT '部门表';
```

### 4.2 用户-部门关联表（多部门）

```sql
CREATE TABLE sys_user_dept (
    id          BIGINT   NOT NULL PRIMARY KEY,
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    dept_id     BIGINT   NOT NULL COMMENT '部门ID',
    is_primary  TINYINT  NOT NULL DEFAULT 0 COMMENT '是否主部门：1-是，0-否',
    UNIQUE KEY uk_user_dept (user_id, dept_id)
) COMMENT '用户-部门关联表（支持用户属于多个部门）';
```

### 4.3 角色-自定义部门表（dataScope=5 时生效）

```sql
CREATE TABLE sys_role_dept (
    id       BIGINT NOT NULL PRIMARY KEY,
    role_id  BIGINT NOT NULL COMMENT '角色ID',
    dept_id  BIGINT NOT NULL COMMENT '部门ID',
    UNIQUE KEY uk_role_dept (role_id, dept_id)
) COMMENT '角色-自定义部门关联表';
```

### 4.4 SysUser 补充字段

`sys_user` 表新增冗余字段（方便快速获取主部门，避免每次 JOIN）：

```sql
ALTER TABLE sys_user ADD COLUMN dept_id BIGINT NULL COMMENT '主部门ID（冗余，与 sys_user_dept.is_primary=1 保持同步）';
```

---

## 五、架构组件

### 5.1 整体流程

```
HTTP 请求
  │
  ▼
Gateway → AuthGlobalFilter 注入 X-User-Id / X-Tenant-Id 请求头
  │
  ▼
业务服务 → TtlFeignRequestInterceptor 传播请求头到 TenantContextHolder
  │
  ▼
DataScopeFilter（Servlet Filter，Order=-100）
  │  从 TenantContextHolder 取 userId
  │  查 Redis：data:scope:{tenantId}:{userId}
  │  将 DataScopeContext 存入 DataScopeContextHolder（TTL）
  │
  ▼
Mapper 方法（标注了 @DataScope）
  │
  ▼
DataPermissionInterceptor → ErpDataPermissionHandler.getSqlSegment()
  │  读取 DataScopeContext
  │  动态拼接 WHERE 条件
  │
  ▼
实际执行带数据权限过滤的 SQL
  │
  ▼
DataScopeFilter finally → DataScopeContextHolder.clear()
```

### 5.2 erp-common-mybatis 新增组件清单

| 组件 | 类型 | 职责 |
|------|------|------|
| `@DataScope` | 注解 | 标注在 Mapper 方法上，声明 deptAlias / userAlias |
| `DataScopeContext` | DTO | 承载当前用户的 dataScope 值和部门ID集合 |
| `DataScopeContextHolder` | TTL工具类 | 线程安全存取 DataScopeContext |
| `ErpDataPermissionHandler` | 接口实现 | 实现 MP 的 DataPermissionHandler，动态生成 WHERE 片段 |
| `DataScopeFilter` | Servlet Filter | 请求开始时从缓存加载 DataScopeContext，结束时清理 |

---

## 六、核心组件设计

### 6.1 `@DataScope` 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {
    /**
     * dept_id 字段所在表的别名（如 "t"、"o"）
     * 为空则不做部门维度过滤
     */
    String deptAlias() default "";

    /**
     * create_by 字段所在表的别名
     * 为空则不做本人维度过滤
     */
    String userAlias() default "";
}
```

### 6.2 `DataScopeContext` 数据结构

```java
public class DataScopeContext implements Serializable {
    private Integer dataScope;      // 1-5，当前用户有效权限档位（多角色取最小值）
    private Long    userId;         // 当前用户ID
    private Long    primaryDeptId;  // 主部门ID
    private Set<Long> deptIds;      // 有效部门ID集合
                                    //   scope=2：仅主部门
                                    //   scope=3：主部门+所有子孙部门
                                    //   scope=5：角色指定的自定义部门列表
}
```

### 6.3 `ErpDataPermissionHandler` SQL 拼接逻辑

```
scope=1 → 返回 null（MP 不注入任何条件）
scope=2 → {deptAlias}.dept_id = {primaryDeptId}
scope=3 → {deptAlias}.dept_id IN ({deptIds 逗号列表})
scope=4 → {userAlias}.create_by = {userId}
scope=5 → {deptAlias}.dept_id IN ({deptIds 逗号列表})

当 deptAlias 为空但 userAlias 不为空时（scope=2/3/5），降级为 create_by 过滤
当 DataScopeContext 为 null 时（未登录或内部调用）→ 不注入条件，由上层保证安全
```

### 6.4 MybatisPlusConfig 拦截器顺序

```
TenantLineInnerInterceptor  （多租户 Schema 隔离）
DataPermissionInterceptor   ← 新增，紧跟多租户之后
PaginationInnerInterceptor
OptimisticLockerInnerInterceptor
```

---

## 七、缓存策略

```
Redis Key：data:scope:{tenantId}:{userId}
Value：DataScopeContext（JSON 序列化）
TTL：5 分钟

写入时机：
  - 用户登录时由 erp-system 计算并写入
  - 角色变更、部门变更时主动删除对应 key（由 erp-system 负责）

各业务服务：只读缓存，不直接查询 sys_role / sys_user_dept
```

---

## 八、erp-system 新增职责

1. 提供 `DataScopeContext` 的计算逻辑（查用户角色 → 取最小 dataScope → 查部门树/自定义部门 → 组装写入 Redis）
2. 角色或部门关系变更时，清除相关用户的缓存 key
3. 部门树维护：`sys_dept` 的 CRUD 接口，维护 `ancestors` 字段（用于快速子树查询，避免递归）

---

## 九、业务服务接入方式（以 erp-sale 为例）

```java
// Mapper 接口
@DataScope(deptAlias = "o", userAlias = "o")
List<SaleOrder> selectPageByQuery(@Param("query") SaleOrderQuery query);

// 要求业务表含以下字段（BaseEntity 已有 create_by）：
//   dept_id  BIGINT  -- 数据归属部门
//   create_by BIGINT -- 创建人（BaseEntity 已有）
```

**接入成本：**
1. 业务表加 `dept_id` 字段
2. 需要权限过滤的 Mapper 方法加 `@DataScope` 注解
3. 写入数据时填充 `deptId`（可在 `ErpMetaObjectHandler` 中统一处理）

---

## 十、内部服务调用豁免

Feign 服务间调用（如 erp-sale 调用 erp-inventory）不应触发数据权限过滤。

**方案：** 内部调用通过 `X-Inner-Call: true` 请求头标识，`DataScopeFilter` 检测到该头时跳过缓存加载，`DataScopeContextHolder` 保持 null，`ErpDataPermissionHandler` 对 null 上下文不注入条件。

---

## 十一、验收标准

1. `erp-common-mybatis` 编译通过，包含 `@DataScope` 注解、`DataScopeContextHolder`、`ErpDataPermissionHandler`、`DataScopeFilter`
2. `erp-system` 登录成功后 Redis 中可查到 `data:scope:{tenantId}:{userId}` 缓存
3. `erp-sale` Mapper 标注 `@DataScope` 后，执行查询的实际 SQL 中包含 `dept_id IN (...)` 或 `create_by = ?` 条件
4. `dataScope=1` 的角色查询不附加任何过滤条件
5. 内部 Feign 调用（携带 `X-Inner-Call: true`）不触发数据权限过滤
