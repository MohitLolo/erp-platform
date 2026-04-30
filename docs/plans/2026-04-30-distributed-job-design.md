# 分布式定时任务设计文档

**日期：** 2026-04-30
**状态：** 已确认，待实施

---

## 一、背景

ERP 系统中存在多类典型定时任务场景：

- 库存预警（定时扫描低库存 SKU，推送告警）
- 账期对账（定时核对应收/应付账款）
- 报表汇总（定时聚合各业务数据生成报表）
- 订单超时自动取消（扫描超时未付款/未确认订单）
- 数据同步（跨系统数据定时同步）

项目为微服务架构，多实例部署，Spring 原生 `@Scheduled` 在多实例下会重复执行，需要引入分布式定时任务框架解决。

---

## 二、技术选型

### 候选方案对比

| 方案 | 优势 | 劣势 |
|------|------|------|
| Spring @Scheduled + Redis 分布式锁 | 无额外依赖 | 无可视化、无执行日志、需自行实现幂等 |
| ShedLock + Spring Scheduler | 轻量，无额外服务 | 功能弱，无任务管理界面，无手动触发 |
| XXL-JOB | 成熟稳定，可视化控制台，支持手动触发/日志/报警/分片 | 需单独部署 Admin 服务 |
| PowerJob | 功能更强（工作流、Map-Reduce） | 更重，ERP 场景暂不需要 |

### 决策：采用 XXL-JOB 2.4.1

理由：
1. ERP 场景需要可视化管理（手动触发对账、查看执行日志）
2. 部分任务（如报表汇总）后续需要分片执行
3. 运维上可接受单独部署 Admin 控制台
4. 社区成熟，Spring Boot 3 兼容性好

---

## 三、模块设计

### 3.1 新增模块 `erp-common-job`

在 `erp-commons` 下新增 `erp-common-job` 模块：

```
erp-commons/
└── erp-common-job/
    ├── pom.xml
    └── src/main/java/com/erp/common/job/
    │   └── config/
    │       └── XxlJobConfig.java
    └── src/main/resources/
        ├── META-INF/spring/
        │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
        └── xxl-job-default.yml        ← 默认配置（可被业务服务覆盖）
```

**定位：**
- `packaging=jar`，不可独立运行，仅作公共库
- 薄封装：依赖收口 + 自动配置，不做业务抽象
- 按需引入：无定时任务的服务（erp-auth、erp-gateway 等）不引入，零侵入

### 3.2 依赖关系

```
erp-common-job
  ├── xxl-job-core (2.4.1)
  └── erp-common-core
```

版本在根 `pom.xml` 的 `<dependencyManagement>` 中统一声明：

```xml
<dependency>
    <groupId>com.xuxueli</groupId>
    <artifactId>xxl-job-core</artifactId>
    <version>2.4.1</version>
</dependency>
```

---

## 四、核心组件设计

### 4.1 `XxlJobConfig.java`

```java
@Configuration
@ConditionalOnProperty(prefix = "xxl.job", name = "admin.addresses")
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    @Value("${xxl.job.executor.appname:${spring.application.name}}")
    private String appname;

    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    @Value("${xxl.job.executor.port:9100}")
    private int port;

    @Value("${xxl.job.executor.logpath:/data/applogs/xxl-job}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
```

`@ConditionalOnProperty` 保证未配置 `xxl.job.admin.addresses` 时 Bean 不注册，服务可安全引入依赖而不启动 Executor。

---

## 五、配置规范

### 5.1 默认配置（`erp-common-job` 内置）

```yaml
xxl:
  job:
    executor:
      appname: ${spring.application.name}   # 自动取服务名
      address: ""                            # 留空，自动取 Pod IP
      ip: ""                                 # 留空，自动取
      port: 9100                             # 统一端口，容器部署 IP 不同无冲突
      logpath: /data/applogs/xxl-job
      logretentiondays: 30
    accessToken: ${XXL_JOB_ACCESS_TOKEN:}
```

### 5.2 各业务服务只需覆盖 Admin 地址

```yaml
# 各业务服务 application.yml
xxl:
  job:
    admin:
      addresses: http://xxl-job-admin.xxl-job.svc.cluster.local:8080/xxl-job-admin
```

---

## 六、K8s 部署可达性方案

XXL-JOB 的调度链路：**Executor → Admin（注册/心跳）+ Admin → Executor（调度回调）**

在 K8s 环境下：

| 方向 | 走什么 | 配置要点 |
|------|--------|---------|
| Executor → Admin | K8s Service ClusterIP DNS | `addresses` 使用 `xxl-job-admin.{namespace}.svc.cluster.local` |
| Admin → Executor | Pod IP 直连 | Executor `ip/address` 留空，框架自动上报 Pod eth0 IP |

**约束：**
1. XXL-JOB Admin **必须部署在同一 K8s 集群**，建议单独 Namespace（如 `xxl-job`）
2. Executor Pod 需在容器定义中暴露 `9100` 端口（`containerPort: 9100`），供 Admin 直连
3. NetworkPolicy 不得拦截 Admin Pod → Executor Pod 的 9100 端口流量
4. Admin 自身通过 ClusterIP Service 对业务服务暴露，无需 NodePort / Ingress（仅集群内使用）

---

## 七、业务服务接入方式

### 7.1 引入依赖

```xml
<dependency>
    <groupId>com.erp.commons</groupId>
    <artifactId>erp-common-job</artifactId>
</dependency>
```

### 7.2 编写 JobHandler

```java
@Component
public class InventoryAlertJob {

    @XxlJob("inventoryAlertHandler")
    public void execute() {
        // 业务逻辑
        XxlJobHelper.log("库存预警任务开始执行");
        // ...
        XxlJobHelper.handleSuccess();
    }
}
```

### 7.3 在 XXL-JOB Admin 控制台注册任务

- AppName：与 `spring.application.name` 一致（自动上报）
- JobHandler：填写 `@XxlJob` 中的 Handler 名称
- Cron：配置执行周期

**接入成本：**
1. `pom.xml` 引入 `erp-common-job`
2. `application.yml` 配置 Admin 地址
3. 编写 Handler 类，加 `@XxlJob` 注解
4. Admin 控制台添加任务配置

---

## 八、需要引入定时任务的服务清单（预估）

| 服务 | 典型任务 |
|------|---------|
| erp-inventory | 库存预警扫描 |
| erp-sale | 订单超时自动取消 |
| erp-finance | 账期对账、逾期提醒 |
| erp-report | 日报/周报汇总生成 |
| erp-purchase | 采购单超时提醒 |

其余服务（erp-auth、erp-system、erp-base、erp-gateway）暂不引入。

---

## 九、验收标准

1. `erp-common-job` 编译通过，`XxlJobSpringExecutor` Bean 可正常注册
2. 未配置 `xxl.job.admin.addresses` 时，引入依赖的服务正常启动，不报错
3. `erp-inventory`（或任一业务服务）引入后，能在 XXL-JOB Admin 控制台看到 Executor 注册成功
4. 手动触发任务，Admin 能成功回调 Executor Pod IP:9100，任务执行日志可查
