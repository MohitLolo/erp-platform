package com.erp.common.redis.redisson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * Redisson 布隆过滤器工具类
 *
 * <p>用于防止缓存穿透等场景。
 *
 * <p>使用示例：
 * <pre>
 *   // 初始化（预计 100 万条数据，误判率 0.01%）
 *   bloomFilterHelper.init("product:ids", 1_000_000L, 0.001);
 *
 *   // 添加元素
 *   bloomFilterHelper.add("product:ids", productId.toString());
 *
 *   // 判断是否存在
 *   if (!bloomFilterHelper.contains("product:ids", productId.toString())) {
 *       return null; // 一定不存在，直接返回
 *   }
 * </pre>
 *
 * @author erp
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonBloomFilterHelper {

    private final RedissonClient redissonClient;

    /**
     * 初始化布隆过滤器
     *
     * @param filterName    过滤器名称（Redis Key）
     * @param expectedInsertions 预期插入数量
     * @param falseProbability   误判率（例如 0.001 = 0.1%）
     */
    public void init(String filterName, long expectedInsertions, double falseProbability) {
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(filterName);
        bloomFilter.tryInit(expectedInsertions, falseProbability);
    }

    /**
     * 向布隆过滤器中添加元素
     *
     * @param filterName 过滤器名称
     * @param value      要添加的值
     * @return true=添加成功（未重复）
     */
    public boolean add(String filterName, Object value) {
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(filterName);
        return bloomFilter.add(value);
    }

    /**
     * 判断元素是否可能存在
     *
     * @param filterName 过滤器名称
     * @param value      要查询的值
     * @return false=一定不存在；true=可能存在（有误判率）
     */
    public boolean contains(String filterName, Object value) {
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(filterName);
        return bloomFilter.contains(value);
    }
}
