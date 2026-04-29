package com.erp.common.redis.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 Redis Lettuce 连接池默认配置。
 * 最低优先级，各服务 application.yml 中的同名配置会自动覆盖（如 erp-inventory 有自定义池大小）。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/redis-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class RedisDefaultsAutoConfiguration {
}
