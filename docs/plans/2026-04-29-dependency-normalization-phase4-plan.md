# 依赖规范化计划（四期：Web/Actuator/Validation/Metrics）

日期：2026-04-29

## 目标
- 在方案B下统一基础 Web 类依赖边界。
- 覆盖：`spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-actuator`、`micrometer-registry-prometheus`。

## 原则
1. 公共模块托管横切依赖，服务侧不重复声明。
2. 最小改动：先收敛到 `erp-common-web`（web+validation）。
3. 监控依赖（actuator/micrometer）暂留服务侧显式声明（便于按服务裁剪）。

## 执行步骤
1. 扫描服务侧上述依赖重复情况。
2. 在 `erp-common-web` 中补齐 `spring-boot-starter-validation`。
3. 删除已引入 `erp-common-web` 的服务中重复 `spring-boot-starter-web` 和 `spring-boot-starter-validation`。
4. 保留 gateway（reactive）和未引入 common-web 的服务的必要 web 依赖。
5. 全量编译验证。

## 验收标准
- 引入 `erp-common-web` 的服务不再重复声明 `spring-boot-starter-web` / `spring-boot-starter-validation`。
- `actuator/micrometer` 维持现状。
- 全量编译通过。
