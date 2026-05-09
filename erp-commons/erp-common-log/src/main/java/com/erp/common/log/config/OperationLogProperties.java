package com.erp.common.log.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 操作日志配置项。
 *
 * <p>对应设计文档 §5.4 配置项约定。整体能力默认关闭（{@link #enabled} = false），
 * 业务服务在自己的 {@code application.yml} 中通过 {@code erp.operation-log.enabled=true} 开启。</p>
 *
 * <pre>
 * erp:
 *   operation-log:
 *     enabled: true
 *     retain-days: 90
 *     permission-resolver:
 *       l1-max-size: 10000
 *       l1-ttl: 10m
 *       l2-redis-key: "erp:permission:all"
 *       l2-timeout: 200ms
 *       warmup-timeout: 3s
 *     async:
 *       core-pool-size: 4
 *       max-pool-size: 16
 *       queue-capacity: 1000
 *       keepalive: 60s
 *       shutdown-await: 5s
 * </pre>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "erp.operation-log")
public class OperationLogProperties {

    /**
     * 总开关，默认 {@code false} —— 引入依赖 ≠ 启用能力，必须显式打开（设计 §7.5.2）。
     */
    private boolean enabled = false;

    /**
     * 日志保留天数（XXL-JOB 清理任务读取该值），默认 90 天。
     */
    @Min(1)
    private int retainDays = 90;

    /**
     * 权限名解析器（两级缓存）配置。
     */
    @NotNull
    private PermissionResolver permissionResolver = new PermissionResolver();

    /**
     * 异步写入器配置。
     */
    @NotNull
    private Async async = new Async();

    @Data
    public static class PermissionResolver {
        /** L1 Caffeine 容量。 */
        @Min(100)
        private long l1MaxSize = 10_000L;
        /** L1 Caffeine TTL（写入后过期）。 */
        @NotNull
        private Duration l1Ttl = Duration.ofMinutes(10);
        /** L2 Redis Hash key。 */
        @NotNull
        private String l2RedisKey = "erp:permission:all";
        /** L2 Redis 单次操作硬性超时，超过则降级返回 null。 */
        @NotNull
        private Duration l2Timeout = Duration.ofMillis(200);
        /** 启动预热超时；预热失败不阻止 Bean 创建。 */
        @NotNull
        private Duration warmupTimeout = Duration.ofSeconds(3);
        /** 是否暴露 Caffeine stats（便于 micrometer 绑定）。 */
        private boolean recordStats = true;
    }

    @Data
    public static class Async {
        /** 异步线程池核心大小。 */
        @Min(1)
        private int corePoolSize = 4;
        /** 异步线程池最大大小。 */
        @Min(1)
        private int maxPoolSize = 16;
        /** 队列容量，溢出走 DiscardOldestPolicy。 */
        @Min(1)
        private int queueCapacity = 1000;
        /** 空闲线程存活时间。 */
        @NotNull
        private Duration keepalive = Duration.ofSeconds(60);
        /** 优雅停机等待时长。 */
        @NotNull
        private Duration shutdownAwait = Duration.ofSeconds(5);
        /** 线程名前缀（线程 dump 容易定位）。 */
        @NotNull
        private String threadNamePrefix = "op-log-";
    }
}
