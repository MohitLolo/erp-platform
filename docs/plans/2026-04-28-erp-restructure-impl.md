# ERP 微服务架构重构实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将现有平铺模块结构重构为三层父模块结构（erp-commons / erp-apis / erp-services），集成 TTL / Redisson / smart-doc，提升解耦性和可维护性。

**Architecture:** 根 pom 负责版本仲裁；erp-commons 包含 7 个公共子模块（core/log/auth/redis/mybatis/mq/web），发布到 Maven 私服；erp-apis 包含 4 个服务间 API 模块，独立版本发布到私服；erp-services 包含 10 个业务服务，仅打 Docker 镜像。

**Tech Stack:** Spring Boot 3.2.5 / Java 21 / TransmittableThreadLocal 2.14.5 / Redisson 3.27.2 / smart-doc 3.0.3 / Sa-Token 1.38.0 / MyBatis-Plus 3.5.6 / Seata 2.1.0

---

## Task 1: 重构根 pom.xml

**Files:**
- Modify: `pom.xml`

**Steps:**
1. 将 `<modules>` 改为 `erp-commons`、`erp-apis`、`erp-services`
2. 在 `<properties>` 中新增版本号：
   - `transmittable-thread-local.version` = 2.14.5
   - `redisson.version` = 3.27.2
   - `smart-doc.version` = 3.0.3
   - `sentinel.version` = 1.8.8
3. 删除 `knife4j` 依赖（替换为 smart-doc，零侵入不需要 knife4j starter）
4. 在 dependencyManagement 中添加所有 erp-common-* 和 erp-api-* 内部模块声明（groupId=com.erp.commons / com.erp.api）
5. 添加 `distributionManagement`（Nexus releases + snapshots）
6. 添加 `redisson-spring-boot-starter` 和 `transmittable-thread-local` 到 dependencyManagement

---

## Task 2: 创建 erp-commons/pom.xml

**Files:**
- Create: `erp-commons/pom.xml`

**Steps:**
1. 创建目录 `erp-commons/`
2. 编写父 pom，groupId=com.erp.commons，声明 7 个子模块
3. 添加 distributionManagement 继承自根 pom

---

## Task 3: 创建 erp-common-core

**Files:**
- Create: `erp-commons/erp-common-core/pom.xml`
- Create: `erp-commons/erp-common-core/src/main/java/com/erp/common/core/context/TenantContextHolder.java`
- Create: `erp-commons/erp-common-core/src/main/java/com/erp/common/core/response/R.java`
- Create: `erp-commons/erp-common-core/src/main/java/com/erp/common/core/response/ResultCode.java`
- Create: `erp-commons/erp-common-core/src/main/java/com/erp/common/core/exception/BizException.java`
- Create: `erp-commons/erp-common-core/src/main/java/com/erp/common/core/constant/HeaderConstants.java`
- Create: `erp-commons/erp-common-core/src/main/java/com/erp/common/core/entity/BaseEntity.java`

---

## Task 4: 创建 erp-common-log

**Files:**
- Create: `erp-commons/erp-common-log/pom.xml`
- Create: `erp-commons/erp-common-log/src/main/java/com/erp/common/log/filter/MdcContextFilter.java`
- Create: `erp-commons/erp-common-log/src/main/resources/logback-spring.xml`

---

## Task 5: 创建 erp-common-auth

**Files:**
- Create: `erp-commons/erp-common-auth/pom.xml`
- Create: `erp-commons/erp-common-auth/src/main/java/com/erp/common/auth/util/JwtUtil.java`

---

## Task 6: 创建 erp-common-redis（含 Redisson）

**Files:**
- Create: `erp-commons/erp-common-redis/pom.xml`
- Create: `erp-commons/erp-common-redis/src/main/java/com/erp/common/redis/redisson/RedissonLockHelper.java`
- Create: `erp-commons/erp-common-redis/src/main/java/com/erp/common/redis/redisson/RedissonRateLimiterHelper.java`
- Create: `erp-commons/erp-common-redis/src/main/java/com/erp/common/redis/redisson/RedissonBloomFilterHelper.java`

---

## Task 7: 创建 erp-common-mybatis

**Files:**
- Create: `erp-commons/erp-common-mybatis/pom.xml`
- Create: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/config/MybatisPlusConfig.java`
- Create: `erp-commons/erp-common-mybatis/src/main/java/com/erp/common/mybatis/handler/ErpMetaObjectHandler.java`

---

## Task 8: 创建 erp-common-mq

**Files:**
- Create: `erp-commons/erp-common-mq/pom.xml`
- Create: `erp-commons/erp-common-mq/src/main/java/com/erp/common/mq/event/BaseEvent.java`
- Create: `erp-commons/erp-common-mq/src/main/java/com/erp/common/mq/publisher/EventPublisher.java`

---

## Task 9: 创建 erp-common-web

**Files:**
- Create: `erp-commons/erp-common-web/pom.xml`
- Create: `erp-commons/erp-common-web/src/main/java/com/erp/common/web/exception/GlobalExceptionHandler.java`
- Create: `erp-commons/erp-common-web/src/main/java/com/erp/common/web/feign/TtlFeignRequestInterceptor.java`

---

## Task 10: 创建 erp-apis/pom.xml

**Files:**
- Create: `erp-apis/pom.xml`

---

## Task 11: 创建 erp-api-inventory

**Files:**
- Create: `erp-apis/erp-api-inventory/pom.xml`
- Create: `erp-apis/erp-api-inventory/src/main/java/com/erp/api/inventory/feign/InventoryFeignClient.java`
- Create: `erp-apis/erp-api-inventory/src/main/java/com/erp/api/inventory/dto/LockStockRequest.java`

---

## Task 12: 创建 erp-api-finance

**Files:**
- Create: `erp-apis/erp-api-finance/pom.xml`
- Create: `erp-apis/erp-api-finance/src/main/java/com/erp/api/finance/feign/FinanceFeignClient.java`
- Create: `erp-apis/erp-api-finance/src/main/java/com/erp/api/finance/dto/CreateReceivableRequest.java`

---

## Task 13: 创建 erp-api-system + erp-api-base

**Files:**
- Create: `erp-apis/erp-api-system/pom.xml`
- Create: `erp-apis/erp-api-system/src/main/java/com/erp/api/system/feign/SystemUserFeignClient.java`
- Create: `erp-apis/erp-api-system/src/main/java/com/erp/api/system/dto/UserInfoDTO.java`
- Create: `erp-apis/erp-api-base/pom.xml`
- Create: `erp-apis/erp-api-base/src/main/java/com/erp/api/base/feign/MaterialFeignClient.java`
- Create: `erp-apis/erp-api-base/src/main/java/com/erp/api/base/dto/MaterialDTO.java`

---

## Task 14: 创建 erp-services/pom.xml

**Files:**
- Create: `erp-services/pom.xml`

---

## Task 15: 迁移 10 个业务服务到 erp-services/

**Files:**
- Move: `erp-gateway/` → `erp-services/erp-gateway/`
- Move: `erp-auth/` → `erp-services/erp-auth/`
- Move: `erp-system/` → `erp-services/erp-system/`
- Move: `erp-base/` → `erp-services/erp-base/`
- Move: `erp-purchase/` → `erp-services/erp-purchase/`
- Move: `erp-sale/` → `erp-services/erp-sale/`
- Move: `erp-inventory/` → `erp-services/erp-inventory/`
- Move: `erp-finance/` → `erp-services/erp-finance/`
- Move: `erp-production/` → `erp-services/erp-production/`
- Move: `erp-report/` → `erp-services/erp-report/`
- Update all pom.xml parent references
- Update erp-sale imports: remove inline FeignClient, use erp-api-inventory + erp-api-finance

---

## Task 16: 为每个服务添加 smart-doc.json

**Files:**
- Create: `erp-services/*/src/main/resources/smart-doc.json` (9 services except gateway)
- Modify: `erp-services/pom.xml` to add smart-doc-maven-plugin

---

## Task 17: 精简 SQL 文件

**Files:**
- Modify: `sql/00_init_databases.sql` (只保留 erp_system + seata 库)
- Modify: `sql/01_erp_system.sql` (保留)
- Modify: `sql/02_seata.sql` (保留)
- Delete: `sql/03_erp_base.sql`
- Delete: `sql/04_erp_purchase.sql`
- Delete: `sql/05_erp_sale.sql`
- Delete: `sql/06_erp_inventory.sql`
- Delete: `sql/07_erp_finance.sql`
- Delete: `sql/08_erp_production.sql`

---

## Task 18: 删除旧 erp-common/ 模块

**Files:**
- Delete: `erp-common/` (整个目录)

---

## Task 19: 编译验证

```bash
cd /home/lolo/javaproject/simple/erp-platform
mvn compile -DskipTests -pl erp-commons -am 2>&1 | tail -30
mvn compile -DskipTests -pl erp-apis -am 2>&1 | tail -30
```

Expected: BUILD SUCCESS

---

## Task 20: Git 提交

```bash
git add -A
git commit -m "refactor: restructure to three-tier parent module layout (erp-commons/erp-apis/erp-services)"
```
