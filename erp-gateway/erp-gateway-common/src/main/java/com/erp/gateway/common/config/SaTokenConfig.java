package com.erp.gateway.common.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Sa-Token 全局配置（自动装配）
 *
 * <p>注册 SaReactorFilter 用于 WebFlux 环境下的权限校验。
 * 此处仅做登录状态校验，细粒度权限控制在各业务服务内完成。
 *
 * <p>通过 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * 自动注入到各端网关启动类。
 */
@AutoConfiguration
public class SaTokenConfig {

    /**
     * 注册 Sa-Token 全局过滤器
     */
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 白名单（无需登录）
                .addExclude("/api/auth/login", "/api/auth/refresh", "/actuator/**")
                // 异常处理
                .setError(e -> "{\"code\":401,\"msg\":\"" + e.getMessage() + "\",\"data\":null}");
    }
}
