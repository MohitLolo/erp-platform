package com.erp.gateway.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 全局配置
 * 注册 SaReactorFilter 用于WebFlux环境下的权限校验
 */
@Configuration
public class SaTokenConfig {

    /**
     * 注册 Sa-Token 全局过滤器
     * 此处仅做基础校验，细粒度权限控制在各业务服务内完成
     */
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 白名单（无需登录）
                .addExclude("/api/auth/login", "/api/auth/refresh", "/actuator/**")
                // 鉴权方法（仅校验登录状态）
                .setAuth(obj -> {
                    // 网关层只验证是否登录，角色权限控制交给各业务服务
                    SaRouter.match("/**").check(r -> StpUtil.checkLogin());
                })
                // 异常处理
                .setError(e -> "{\"code\":401,\"msg\":\"" + e.getMessage() + "\",\"data\":null}");
    }
}
