package com.erp.gateway.filter;

import com.erp.common.core.constant.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 灰度路由过滤器
 * 支持两种灰度触发方式：
 * 1. 请求头 X-Gray-Tag: v2
 * 2. Cookie gray_user=true（用于灰度用户群体）
 *
 * 灰度流量会被标记后由路由规则转发到 v2 Service
 */
@Slf4j
@Component
public class GrayRoutingFilter implements GlobalFilter, Ordered {

    private static final String GRAY_VALUE = "v2";
    private static final String GRAY_COOKIE = "gray_user";

    @Override
    public int getOrder() {
        // 在Auth过滤器之后执行
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. 检查请求头是否已有灰度标记
        String existingGrayTag = request.getHeaders().getFirst(HeaderConstants.GRAY_TAG);
        if (GRAY_VALUE.equals(existingGrayTag)) {
            log.debug("Gray routing: request already tagged as v2, path={}", request.getPath().value());
            return chain.filter(exchange);
        }

        // 2. 检查Cookie中是否有灰度标记
        boolean isGrayByCookie = request.getCookies()
                .getOrDefault(GRAY_COOKIE, List.of())
                .stream()
                .anyMatch(cookie -> "true".equals(cookie.getValue()));

        if (isGrayByCookie) {
            // 注入灰度标记到请求头，后续路由规则（Header=X-Gray-Tag, v2）会匹配到 v2 路由
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(HeaderConstants.GRAY_TAG, GRAY_VALUE)
                    .build();
            log.debug("Gray routing: cookie triggered, injecting v2 tag, path={}", request.getPath().value());
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        return chain.filter(exchange);
    }
}
