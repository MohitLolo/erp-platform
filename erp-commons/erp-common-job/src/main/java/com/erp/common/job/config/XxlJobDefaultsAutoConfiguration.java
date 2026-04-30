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
