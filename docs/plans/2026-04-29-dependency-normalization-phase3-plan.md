# 依赖规范化计划（三期：Feign/Resilience）

日期：2026-04-29

## 目标
- 延续方案B：由 common 模块托管基础依赖，服务侧删除重复声明。
- 本期覆盖：OpenFeign / Resilience4j。

## 原则
1. `erp-common-web` 托管 `spring-cloud-starter-openfeign`。
2. 服务侧仅在确有直接使用注解/API时保留 `resilience4j-spring-boot3`。
3. 最小改动优先，先清理重复 `openfeign`。

## 执行步骤
1. 扫描服务侧 `openfeign/resilience4j` 声明与源码使用点。
2. 删除与 `erp-common-web` 重复的 `openfeign` 服务侧声明。
3. 保留仍有直接 `@CircuitBreaker` 使用的服务 `resilience4j` 依赖。
4. 全量编译验证。

## 验收标准
- 服务侧无重复 `spring-cloud-starter-openfeign`（由 common-web 提供）。
- `resilience4j` 仅在必要服务保留。
- 全量编译通过。
