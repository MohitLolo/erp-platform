package com.erp.common.web.config;

import com.erp.common.core.config.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * 自动注入 web 层架构级默认配置：优雅关闭、Feign 超时、Actuator、日志格式。
 * 最低优先级，各服务 application.yml 中的同名配置会自动覆盖。
 */
@AutoConfiguration
@PropertySource(
    value = "classpath:erp-defaults/web-defaults.yml",
    factory = YamlPropertySourceFactory.class
)
public class WebDefaultsAutoConfiguration {
}
