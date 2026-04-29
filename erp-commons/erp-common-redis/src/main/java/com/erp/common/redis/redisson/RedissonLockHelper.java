package com.erp.common.redis.redisson;

import com.erp.common.core.exception.BizException;
import com.erp.common.core.response.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式可重入锁工具类
 *
 * <p>使用示例：
 * <pre>
 *   // 函数式（自动加锁/解锁）
 *   String result = lockHelper.executeWithLock("order:lock:" + orderId, 30, TimeUnit.SECONDS, () -> {
 *       return orderService.doProcess(orderId);
 *   });
 *
 *   // 手动模式（tryLock + unlock）
 *   if (lockHelper.tryLock("key", 5, 30, TimeUnit.SECONDS)) {
 *       try { ... } finally { lockHelper.unlock("key"); }
 *   }
 * </pre>
 *
 * @author erp
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockHelper {

    private final RedissonClient redissonClient;

    /**
     * 加锁执行（有返回值），锁不到则抛出业务异常
     *
     * @param lockKey   锁键
     * @param leaseTime 持锁时间
     * @param unit      时间单位
     * @param supplier  业务逻辑
     * @param <T>       返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithLock(String lockKey, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(leaseTime, unit);
        try {
            return supplier.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 加锁执行（无返回值），锁不到则抛出业务异常
     *
     * @param lockKey   锁键
     * @param leaseTime 持锁时间
     * @param unit      时间单位
     * @param runnable  业务逻辑
     */
    public void executeWithLock(String lockKey, long leaseTime, TimeUnit unit, Runnable runnable) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(leaseTime, unit);
        try {
            runnable.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 尝试加锁（非阻塞）
     *
     * @param lockKey   锁键
     * @param waitTime  等待时间
     * @param leaseTime 持锁时间
     * @param unit      时间单位
     * @return true=加锁成功
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("tryLock interrupted, lockKey={}", lockKey);
            return false;
        }
    }

    /**
     * 释放锁（仅当前线程持有时才释放）
     *
     * @param lockKey 锁键
     */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
