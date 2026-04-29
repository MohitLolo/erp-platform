package com.erp.common.mq.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 RabbitMQ 消费者架构级默认配置：手动ACK、重试策略。
 * 最低优先级，各服务 application.yml 中的同名配置会自动覆盖。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/rabbitmq-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class RabbitmqDefaultsAutoConfiguration {
}
