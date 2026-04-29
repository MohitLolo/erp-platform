# 依赖规范化计划（二期：MyBatis/Redis）

日期：2026-04-29

## 目标
- 延续方案B：由 common 模块托管依赖，服务侧删除重复声明。
- 本期覆盖：MyBatis-Plus / MySQL / Redis / Redisson。

## 原则
1. `erp-common-mybatis` 托管 `mybatis-plus` 与 `mysql-connector-j`。
2. `erp-common-redis` 托管 `spring-boot-starter-data-redis` 与 `redisson-spring-boot-starter`。
3. 服务模块只声明 `erp-common-*`，不重复声明上述基础依赖。

## 执行步骤
1. 扫描服务 pom 的重复声明。
2. 删除与 `erp-common-mybatis` 重复的 `mysql-connector-j` 依赖。
3. 复核是否存在服务侧 `mybatis-plus` / `redisson` / `data-redis` 直引（若有则删除）。
4. 全量编译验证。

## 验收标准
- 服务侧不再出现 `mysql-connector-j` 重复声明（使用 `erp-common-mybatis` 的模块）。
- 服务侧不出现 `mybatis-plus` / `redisson` / `data-redis` 直引。
- 全量编译通过。
