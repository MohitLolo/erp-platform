# 分布式定时任务实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `erp-commons` 下新增 `erp-common-job` 模块，薄封装 XXL-JOB，提供统一的 `XxlJobConfig` 自动配置，供各业务服务按需引入。

**Architecture:** `erp-common-job` 仅包含 `XxlJobConfig`（`@ConditionalOnProperty` 保护）+ 默认 YAML 配置，业务服务引入后只需在 `application.yml` 中配置 Admin 地址即可，Handler 直接用原生 `@XxlJob` 注解编写。

**Tech Stack:** XXL-JOB 2.4.1、Spring Boot 3.2.5、Spring Boot AutoConfiguration、Maven 多模块

---

## TodoList

- [ ] Task 1: 根 `pom.xml` 新增 `xxl-job-core` 版本声明
- [ ] Task 2: `erp-commons/pom.xml` 新增 `erp-common-job` 子模块声明
- [ ] Task 3: 新建 `erp-common-job/` 目录结构与 `pom.xml`
- [ ] Task 4: 新增 `XxlJobConfig.java`
- [ ] Task 5: 新增默认配置文件 `xxl-job-defaults.yml`
- [ ] Task 6: 新增 `AutoConfiguration.imports` 注册
- [ ] Task 7: 根 `pom.xml` 新增 `erp-common-job` 依赖管理声明
- [ ] Task 8: 全量编译验证 + commit

---

## Task 1: 根 `pom.xml` 新增 `xxl-job-core` 版本声明

**Files:**
- Modify: `pom.xml`（根目录）

**Step 1: 在 `<properties>` 中新增版本号**

找到：
```xml
        <hutool.version>5.8.26</hutool.version>
```

在其后新增：
```xml
        <!-- 分布式定时任务 -->
        <xxl-job.version>2.4.1</xxl-job.version>
```

**Step 2: 在 `<dependencyManagement>` 的工具类 section 末尾新增**

找到：
```xml
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
```

在其后新增：
```xml
            <!-- ===== XXL-JOB ===== -->
            <dependency>
                <groupId>com.xuxueli</groupId>
                <artifactId>xxl-job-core</artifactId>
                <version>${xxl-job.version}</version>
            </dependency>
```

同时，在 `erp-common-job` 模块版本声明 section（内部公共模块 section）中新增（`erp-common-web` 之后）：
```xml
            <dependency>
                <groupId>com.erp.commons</groupId>
                <artifactId>erp-common-job</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </dependency>
```

---

## Task 2: `erp-commons/pom.xml` 新增子模块声明

**Files:**
- Modify: `erp-commons/pom.xml`

**Step 1: 在 `<modules>` 末尾新增**

找到：
```xml
        <module>erp-common-web</module>
    </modules>
```

改为：
```xml
        <module>erp-common-web</module>
        <module>erp-common-job</module>
    </modules>
```

**Step 2: 验证**

```bash
grep "erp-common-job" /home/lolo/javaproject/simple/erp-platform/erp-commons/pom.xml
```

Expected: 输出一行 `<module>erp-common-job</module>`。

---

## Task 3: 新建 `erp-common-job/` 目录结构与 `pom.xml`

**Files:**
- Create: `erp-commons/erp-common-job/pom.xml`

**Step 1: 创建目录**

```bash
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-commons/erp-common-job/src/main/java/com/erp/common/job/config
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-commons/erp-common-job/src/main/resources/erp-defaults
mkdir -p /home/lolo/javaproject/simple/erp-platform/erp-commons/erp-common-job/src/main/resources/META-INF/spring
```

**Step 2: 写入 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.erp.commons</groupId>
        <artifactId>erp-commons</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>erp-common-job</artifactId>
    <name>erp-common-job</name>
    <description>XXL-JOB 薄封装（依赖收口 + 自动配置），按需引入，不可独立运行</description>

    <dependencies>
        <!-- XXL-JOB 核心 -->
        <dependency>
            <groupId>com.xuxueli</groupId>
            <artifactId>xxl-job-core</artifactId>
        </dependency>
        <!-- 公共 core（常量、上下文等） -->
        <dependency>
            <groupId>com.erp.commons</groupId>
            <artifactId>erp-common-core</artifactId>
        </dependency>
    </dependencies>
</project>
```

**注意：** 无 `spring-boot-maven-plugin`，`erp-common-job` 是纯 jar 库。

---

## Task 4: 新增 `XxlJobConfig.java`

**Files:**
- Create: `erp-commons/erp-common-job/src/main/java/com/erp/common/job/config/XxlJobConfig.java`

**Step 1: 创建文件**

```java
package com.erp.common.job.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB Executor 自动配置
 *
 * <p>仅在配置了 {@code xxl.job.admin.addresses} 时生效（{@code @ConditionalOnProperty} 保护），
 * 未配置该属性的服务引入此模块后不会注册 Executor Bean，正常启动不报错。
 *
 * <p>各业务服务只需在 {@code application.yml} 中配置：
 * <pre>
 * xxl:
 *   job:
 *     admin:
 *       addresses: http://xxl-job-admin.xxl-job.svc.cluster.local:8080/xxl-job-admin
 * </pre>
 * 其余配置项均有默认值，无需重复声明。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "xxl.job", name = "admin.addresses")
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    /**
     * 默认取 spring.application.name，各服务无需手动配置 appname。
     */
    @Value("${xxl.job.executor.appname:${spring.application.name}}")
    private String appname;

    /**
     * 留空：框架自动取 Pod eth0 IP 上报给 Admin，适配 K8s 容器环境。
     */
    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    /**
     * 统一端口 9100，K8s 容器部署 Pod IP 不同，端口统一无冲突。
     */
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

        log.info("XXL-JOB Executor initialized: appname={}, port={}, admin={}",
                appname, port, adminAddresses);
        return executor;
    }
}
```

---

## Task 5: 新增默认配置文件

**Files:**
- Create: `erp-commons/erp-common-job/src/main/resources/erp-defaults/xxl-job-defaults.yml`

**Step 1: 创建文件**

```yaml
# XXL-JOB Executor 默认配置
# 业务服务只需覆盖 xxl.job.admin.addresses，其余默认值继承此处
xxl:
  job:
    executor:
      # 自动取 spring.application.name，无需手动配置
      appname: ${spring.application.name}
      # 留空：自动取 Pod IP（K8s 容器环境 Admin 回调可达）
      address: ""
      ip: ""
      # 统一 executor 端口，K8s 每个 Pod IP 不同，端口统一无冲突
      port: 9100
      logpath: /data/applogs/xxl-job
      logretentiondays: 30
    # 访问令牌，从环境变量注入
    accessToken: ${XXL_JOB_ACCESS_TOKEN:}
```

**Step 2: 注册 PropertySource**

`XxlJobConfig` 已通过 `@ConditionalOnProperty` 条件加载，默认配置需要一个 `@AutoConfiguration` 类来注入 PropertySource（参考 `erp-common-redis` 的做法）。

创建 `erp-commons/erp-common-job/src/main/java/com/erp/common/job/config/XxlJobDefaultsAutoConfiguration.java`：

```java
package com.erp.common.job.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 XXL-JOB 架构级默认配置（最低优先级）。
 * 各服务 application.yml 中的同名配置会自动覆盖此处默认值。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/xxl-job-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class XxlJobDefaultsAutoConfiguration {
    // 仅负责属性注入，Bean 定义在 XxlJobConfig
}
```

---

## Task 6: 新增 `AutoConfiguration.imports` 注册

**Files:**
- Create: `erp-commons/erp-common-job/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Step 1: 写入内容**

```
com.erp.common.job.config.XxlJobDefaultsAutoConfiguration
com.erp.common.job.config.XxlJobConfig
```

---

## Task 7: 根 `pom.xml` 新增 `erp-common-job` 依赖管理声明

此步骤已在 Task 1 Step 2 中一并完成（`erp-common-job` 的版本已在内部公共模块 section 新增）。

**验证：**

```bash
grep "erp-common-job" /home/lolo/javaproject/simple/erp-platform/pom.xml
```

Expected: 输出包含 `erp-common-job` 的依赖声明行。

---

## Task 8: 全量编译验证 + commit

**Step 1: 编译 erp-common-job**

```bash
cd /home/lolo/javaproject/simple/erp-platform && mvn compile -DskipTests -pl erp-commons/erp-common-job -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

**Step 2: 全量编译**

```bash
cd /home/lolo/javaproject/simple/erp-platform && mvn compile -DskipTests 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

**Step 3: 验证 `@ConditionalOnProperty` 保护**

通过全量编译成功即可确认：所有未配置 `xxl.job.admin.addresses` 的已有服务编译不受影响。

**Step 4: 提交**

```bash
git -C /home/lolo/javaproject/simple/erp-platform add \
  pom.xml \
  erp-commons/pom.xml \
  erp-commons/erp-common-job/

git -C /home/lolo/javaproject/simple/erp-platform commit -m "feat: 新增 erp-common-job 模块，薄封装 XXL-JOB 2.4.1

XxlJobConfig 通过 @ConditionalOnProperty 条件加载，
统一 Executor 端口 9100，适配 K8s Pod IP 直连调度模式。
未配置 admin.addresses 的服务引入依赖后正常启动不受影响。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```
