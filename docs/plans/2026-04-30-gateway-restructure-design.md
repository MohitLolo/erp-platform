# Gateway 多端拆分架构设计

**日期：** 2026-04-30
**状态：** 已确认，待实施

---

## 一、背景

当前 `erp-gateway` 位于 `erp-services/` 下，与业务服务并列。存在两个问题：

1. **语义错误**：网关是流量入口基础设施，不是业务服务，不应与 `erp-sale`、`erp-system` 等并列
2. **技术隐患**：`erp-services/pom.xml` 给所有子模块引入了 `erp-common-web`（Spring MVC Servlet），而网关是 WebFlux，两者不兼容，靠隐式 exclusion 兜住，存在风险

同时，项目后续有多端多业务场景规划（PC端、移动端 App、开放平台等），需要支持多网关独立部署。

---

## 二、技术决策

### 决策1：鉴权方式统一

全端统一使用 Sa-Token JWT，不区分端鉴权策略。`AuthGlobalFilter` 逻辑完全共用，放入 `erp-gateway-common`。

### 决策2：结构选型

采用**顶层父模块 + 多子网关**模式：
- 抽离为根级 `erp-gateway/` 父模块，脱离 `erp-services`
- 共用逻辑集中在 `erp-gateway-common`（纯 JAR 库，不可独立部署）
- 各端网关为独立 Spring Boot 可运行应用，各自打 Docker 镜像
- 当前只实现 `erp-gateway-pc`，`erp-gateway-app` / `erp-gateway-open` 建骨架不部署

---

## 三、目标目录结构

```
erp-platform/
├── erp-commons/
├── erp-apis/
├── erp-services/              ← 移除 erp-gateway，只含业务服务
├── erp-gateway/               ← 新增顶层模块
│   ├── pom.xml                ← 父 pom，packaging=pom，声明3个子模块
│   ├── erp-gateway-common/    ← 共享逻辑，packaging=jar
│   │   ├── pom.xml
│   │   └── src/main/java/com/erp/gateway/common/
│   │       ├── filter/
│   │       │   ├── AuthGlobalFilter.java
│   │       │   └── GrayRoutingFilter.java
│   │       └── config/
│   │           └── SaTokenConfig.java
│   ├── erp-gateway-pc/        ← PC端，packaging=jar，可运行
│   │   ├── pom.xml
│   │   └── src/main/java/com/erp/gateway/pc/
│   │       └── PcGatewayApplication.java
│   │   └── src/main/resources/
│   │       └── application.yml
│   ├── erp-gateway-app/       ← 移动端骨架，暂不部署
│   │   ├── pom.xml
│   │   └── src/main/java/com/erp/gateway/app/
│   │       └── AppGatewayApplication.java
│   └── erp-gateway-open/      ← 开放平台骨架，暂不部署
│       ├── pom.xml
│       └── src/main/java/com/erp/gateway/open/
│           └── OpenGatewayApplication.java
└── pom.xml                    ← 根 pom 新增 erp-gateway 模块
```

---

## 四、依赖关系

```
erp-gateway-pc / erp-gateway-app / erp-gateway-open
  └── erp-gateway-common (jar)
        ├── erp-common-core
        ├── sa-token-reactor-spring-boot3-starter
        ├── sa-token-jwt
        ├── sa-token-redis-jackson
        ├── spring-cloud-starter-gateway
        ├── spring-boot-starter-data-redis-reactive
        └── spring-cloud-starter-loadbalancer
```

`erp-gateway-common` 自身无 `spring-boot-maven-plugin`，不可独立运行，仅作库被各端网关引用。

---

## 五、文件迁移清单

| 原路径 | 目标路径 | 动作 |
|--------|----------|------|
| `erp-services/erp-gateway/filter/AuthGlobalFilter.java` | `erp-gateway/erp-gateway-common/filter/AuthGlobalFilter.java` | 移动，package 改为 `com.erp.gateway.common.filter` |
| `erp-services/erp-gateway/filter/GrayRoutingFilter.java` | `erp-gateway/erp-gateway-common/filter/GrayRoutingFilter.java` | 移动，package 改为 `com.erp.gateway.common.filter` |
| `erp-services/erp-gateway/config/SaTokenConfig.java` | `erp-gateway/erp-gateway-common/config/SaTokenConfig.java` | 移动，package 改为 `com.erp.gateway.common.config` |
| `erp-services/erp-gateway/GatewayApplication.java` | `erp-gateway/erp-gateway-pc/PcGatewayApplication.java` | 移动，重命名，package 改为 `com.erp.gateway.pc` |
| `erp-services/erp-gateway/src/main/resources/application.yml` | `erp-gateway/erp-gateway-pc/src/main/resources/application.yml` | 移动，`spring.application.name` 改为 `erp-gateway-pc` |
| `erp-services/erp-gateway/pom.xml` | 拆为父 `erp-gateway/pom.xml` + `erp-gateway-pc/pom.xml` | 重写 |

---

## 六、erp-services 变化

`erp-services/pom.xml` 的 `<modules>` 中**移除** `<module>erp-gateway</module>`。

删除目录 `erp-services/erp-gateway/`。

---

## 七、根 pom 变化

`pom.xml` 的 `<modules>` 中新增：

```xml
<module>erp-gateway</module>
```

---

## 八、各端网关端口规划

| 网关 | 端口 | 状态 |
|------|------|------|
| erp-gateway-pc | 9000 | 当前实现 |
| erp-gateway-app | 9001 | 骨架预留 |
| erp-gateway-open | 9002 | 骨架预留 |

---

## 九、验收标准

1. `mvn compile -DskipTests -pl erp-gateway -am` → BUILD SUCCESS
2. `erp-services` 下不再有 `erp-gateway` 目录
3. `erp-gateway-pc` 可正常启动，鉴权过滤器生效
4. `erp-gateway-app` / `erp-gateway-open` 目录存在，含 Application 主类，可编译
