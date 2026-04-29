# 依赖规范化计划（方案B）

日期：2026-04-29

## 目标
- 按模块职责收敛依赖声明，减少重复引入。
- 不引入 CI 强制校验，只做结构规范化。

## 原则
1. common 模块托管其能力所需框架依赖。
2. services 侧不重复声明已由 common 传递提供的依赖。
3. 保持最小改动面，优先消除 sa-token 重复声明。

## 变更范围
- `erp-commons/erp-common-auth/pom.xml`
- `erp-services/*/pom.xml`（除 gateway）

## 执行步骤
1. 在 `erp-common-auth` 中补齐 `sa-token-redis-jackson`。
2. 从引入了 `erp-common-auth` 的服务模块中移除重复 `sa-token-*` 依赖。
3. 保留 `erp-gateway` 的 Reactor 版本 sa-token 依赖，不做改动。
4. 全量编译验证 `mvn -U -DskipTests compile`。

## 验收标准
- 服务 pom 中不再重复声明 `sa-token-*`（gateway 除外）。
- 全量编译通过。
