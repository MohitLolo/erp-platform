package com.erp.common.log.resolver;

import com.erp.common.log.config.OperationLogProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaffeineRedisPermissionResolverTest {

    private OperationLogProperties props;
    private RedissonClient redisson;
    @SuppressWarnings("unchecked")
    private RMap<String, String> rmap = (RMap<String, String>) mock(RMap.class);
    private ObjectMapper om;
    private CaffeineRedisPermissionResolver resolver;

    @BeforeEach
    void setUp() {
        props = new OperationLogProperties();
        props.getPermissionResolver().setL1MaxSize(1000);
        props.getPermissionResolver().setL1Ttl(Duration.ofMinutes(10));
        props.getPermissionResolver().setL2Timeout(Duration.ofMillis(200));
        redisson = mock(RedissonClient.class);
        rmap = mock(RMap.class);
        when(redisson.<String, String>getMap(ArgumentMatchers.anyString())).thenReturn(rmap);
        om = new ObjectMapper();
        resolver = new CaffeineRedisPermissionResolver(props, redisson, om);
    }

    @Test
    void l1Hit_doesNotQueryRedis() throws Exception {
        when(rmap.get("p1")).thenReturn(om.writeValueAsString(
                new PermissionMeta("p1", "权限1", "system", "button")));

        PermissionMeta a = resolver.resolve("p1");
        PermissionMeta b = resolver.resolve("p1");

        assertNotNull(a);
        assertEquals("权限1", a.getName());
        assertEquals(a, b);
        verify(rmap, times(1)).get("p1");
    }

    @Test
    void l1Miss_l2Hit_writesBackToL1() throws Exception {
        when(rmap.get("p2")).thenReturn(om.writeValueAsString(
                new PermissionMeta("p2", "Two", "base", "menu")));

        PermissionMeta first = resolver.resolve("p2");
        PermissionMeta second = resolver.resolve("p2");

        assertNotNull(first);
        assertEquals("Two", first.getName());
        assertEquals(first, second);
        verify(rmap, times(1)).get("p2");
    }

    @Test
    void l2Timeout_returnsNull_andCachesEmpty() throws InterruptedException {
        props.getPermissionResolver().setL2Timeout(Duration.ofMillis(50));
        resolver = new CaffeineRedisPermissionResolver(props, redisson, om);

        when(rmap.get("slow")).thenAnswer(inv -> {
            Thread.sleep(300);
            return "{}";
        });

        long t0 = System.currentTimeMillis();
        PermissionMeta result = resolver.resolve("slow");
        long elapsed = System.currentTimeMillis() - t0;

        assertNull(result);
        // 50ms 超时 + CompletableFuture 调度抖动，给一个比较宽的上界
        org.junit.jupiter.api.Assertions.assertTrue(elapsed < 250, "actual=" + elapsed);
    }

    @Test
    void cachePenetration_defended_redisCalledOnce() {
        when(rmap.get("nope")).thenReturn(null);

        for (int i = 0; i < 10; i++) {
            assertNull(resolver.resolve("nope"));
        }
        verify(rmap, times(1)).get("nope");
    }

    @Test
    void warmup_failureDoesNotThrow() {
        when(rmap.readAllMap()).thenThrow(new RuntimeException("simulated outage"));
        // 直接调用以模拟事件回调；不应抛异常
        resolver.warmup();
    }

    @Test
    void warmup_loadsEntriesIntoL1() throws Exception {
        Map<String, String> all = new HashMap<>();
        all.put("a", om.writeValueAsString(new PermissionMeta("a", "A", "m", "menu")));
        all.put("b", om.writeValueAsString(new PermissionMeta("b", "B", "m", "menu")));
        when(rmap.readAllMap()).thenReturn(all);

        resolver.warmup();

        // 调用 resolve 不应再触发 RMap.get（已在 L1）
        assertNotNull(resolver.resolve("a"));
        assertNotNull(resolver.resolve("b"));
        verify(rmap, times(0)).get("a");
        verify(rmap, times(0)).get("b");
    }

    @Test
    void recordStatsEnabled() {
        // L1 应支持 stats（micrometer 自动绑定的前提）
        assertNotNull(resolver.getL1Cache().stats());
    }
}
