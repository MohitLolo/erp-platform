# 依赖规范化计划（五期：Seata）

日期：2026-04-29

## 目标
- 收敛 `seata-spring-boot-starter` 声明范围，仅保留实际需要分布式事务能力的服务。

## 原则
1. 以代码使用为准（`@GlobalTransactional` / Seata 相关 API）。
2. 不做“为了未来”保留，避免无效启动开销。
3. 最小改动，先清理明确冗余。

## 执行步骤
1. 扫描服务侧 Seata 依赖声明与代码使用点。
2. 删除未使用 Seata 的服务中的 `seata-spring-boot-starter`。
3. 保留确有 Seata 事务注解的服务依赖。
4. 全量编译验证。

## 验收标准
- `seata-spring-boot-starter` 仅出现在实际使用 Seata 的服务。
- 全量编译通过。
