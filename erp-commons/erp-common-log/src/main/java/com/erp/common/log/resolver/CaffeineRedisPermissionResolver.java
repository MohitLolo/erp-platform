package com.erp.common.log.resolver;

import com.erp.common.log.config.OperationLogProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 两级缓存权限名解析器（设计文档 §5）。
 *
 * <ul>
 *   <li><b>L1</b>：Caffeine 进程内缓存，TTL 由配置项 {@code l1Ttl} 控制，命中即返回。</li>
 *   <li><b>L2</b>：Redisson {@code RMap<String,String>}（key 由 {@code l2RedisKey} 配置），
 *       命中即写回 L1。L2 操作通过 {@code CompletableFuture#orTimeout} 实现硬超时降级。</li>
 *   <li><b>穿透防御</b>：未命中也用 {@code Optional.empty()} 写回 L1，避免反复打 Redis。</li>
 *   <li><b>启动预热</b>：监听 {@link ApplicationReadyEvent}（不阻塞启动），从 L2 拉取全量灌 L1，
 *       预热失败仅 WARN，不阻止应用就绪。</li>
 * </ul>
 *
 * <p>L3 Feign 兜底默认未启用——若业务方需要，可通过覆盖本 Bean 自行扩展。</p>
 */
@Slf4j
public class CaffeineRedisPermissionResolver implements PermissionNameResolver {

    private final OperationLogProperties.PermissionResolver props;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, Optional<PermissionMeta>> l1;

    public CaffeineRedisPermissionResolver(OperationLogProperties properties,
                                           RedissonClient redissonClient,
                                           ObjectMapper objectMapper) {
        this.props = properties.getPermissionResolver();
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        Caffeine<Object, Object> b = Caffeine.newBuilder()
                .maximumSize(props.getL1MaxSize())
                .expireAfterWrite(props.getL1Ttl());
        if (props.isRecordStats()) {
            b = b.recordStats();
        }
        this.l1 = b.build();
    }

    @Override
    public PermissionMeta resolve(String permissionCode) {
        if (permissionCode == null || permissionCode.isEmpty()) {
            return null;
        }
        Optional<PermissionMeta> cached = l1.getIfPresent(permissionCode);
        if (cached != null) {
            return cached.orElse(null);
        }
        PermissionMeta fromL2 = loadFromL2(permissionCode);
        l1.put(permissionCode, Optional.ofNullable(fromL2));
        return fromL2;
    }

    private PermissionMeta loadFromL2(String code) {
        try {
            CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
                RMap<String, String> map = redissonClient.getMap(props.getL2RedisKey());
                return map.get(code);
            }).orTimeout(props.getL2Timeout().toMillis(), TimeUnit.MILLISECONDS);
            String json = f.get();
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, PermissionMeta.class);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                log.debug("permission L2 timeout for code={}", code);
            } else {
                log.warn("permission L2 lookup failed for code={}: {}", code, cause.toString());
            }
            return null;
        }
    }

    /**
     * 启动后异步预热——不阻塞应用就绪。预热超时由 {@link OperationLogProperties.PermissionResolver#getWarmupTimeout()} 控制。
     * 失败仅 WARN，能力降级为按需解析。
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        long timeoutMs = props.getWarmupTimeout().toMillis();
        try {
            CompletableFuture<Map<String, String>> f = CompletableFuture.supplyAsync(() -> {
                RMap<String, String> map = redissonClient.getMap(props.getL2RedisKey());
                return map.readAllMap();
            }).orTimeout(timeoutMs, TimeUnit.MILLISECONDS);

            Map<String, String> all = f.get();
            int loaded = 0;
            for (Map.Entry<String, String> e : all.entrySet()) {
                try {
                    PermissionMeta meta = objectMapper.readValue(e.getValue(), PermissionMeta.class);
                    l1.put(e.getKey(), Optional.of(meta));
                    loaded++;
                } catch (Exception parseErr) {
                    log.debug("skip invalid permission cache entry: {}", e.getKey());
                }
            }
            log.info("operation-log permission L1 warmup done, loaded={} entries from {}", loaded, props.getL2RedisKey());
        } catch (Exception e) {
            log.warn("operation-log permission L1 warmup failed (timeout={}ms): {}", timeoutMs, e.toString());
        }
    }

    /** 暴露给 micrometer / 测试。 */
    public Cache<String, Optional<PermissionMeta>> getL1Cache() {
        return l1;
    }
}
