package com.erp.common.redis.redisson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * Redisson 分布式限流器工具类
 *
 * <p>基于 Redis 令牌桶算法，支持集群级别的精准限流。
 *
 * <p>使用示例：
 * <pre>
 *   // 初始化：每秒最多 100 次
 *   rateLimiterHelper.setRate("api:queryOrder", 100, 1, RateIntervalUnit.SECONDS);
 *
 *   // 尝试获取令牌
 *   if (!rateLimiterHelper.tryAcquire("api:queryOrder")) {
 *       throw new BizException(ResultCode.TOO_MANY_REQUESTS);
 *   }
 * </pre>
 *
 * @author erp
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonRateLimiterHelper {

    private final RedissonClient redissonClient;

    /**
     * 设置（初始化）限流规则
     *
     * @param key          限流键
     * @param rate         速率（单位时间内允许的请求数）
     * @param rateInterval 时间间隔
     * @param unit         时间单位
     */
    public void setRate(String key, long rate, long rateInterval, RateIntervalUnit unit) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, unit);
    }

    /**
     * 尝试获取 1 个令牌（非阻塞）
     *
     * @param key 限流键
     * @return true=获取成功（未超限）
     */
    public boolean tryAcquire(String key) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        return rateLimiter.tryAcquire();
    }

    /**
     * 尝试获取 N 个令牌（非阻塞）
     *
     * @param key    限流键
     * @param permits 需要获取的令牌数
     * @return true=获取成功
     */
    public boolean tryAcquire(String key, long permits) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        return rateLimiter.tryAcquire(permits);
    }
}
