package com.erp.gateway.filter;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.erp.common.core.constant.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sa-Token JWT 鉴权过滤器
 * 1. 白名单路径直接放行
 * 2. 验证JWT token有效性
 * 3. 将用户信息（userId, tenantId, userName）注入到下游请求头
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Value("${gateway.auth.whitelist:}")
    private List<String> whitelist;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public int getOrder() {
        // 最高优先级，先于灰度路由执行
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 1. 白名单放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 2. 设置Sa-Token Reactor上下文
        SaReactorSyncHolder.setContext(exchange);

        try {
            // 3. 验证token（Sa-Token JWT模式会自动解析Header中的Authorization）
            StpUtil.checkLogin();

            // 4. 获取用户信息并注入下游请求头
            long userId = StpUtil.getLoginIdAsLong();
            Object tenantId = StpUtil.getExtra("tenantId");
            Object userName = StpUtil.getExtra("userName");

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(HeaderConstants.USER_ID, String.valueOf(userId))
                    .header(HeaderConstants.TENANT_ID, tenantId != null ? tenantId.toString() : "")
                    .header(HeaderConstants.USER_NAME, userName != null ? userName.toString() : "")
                    .build();

            log.debug("Auth passed: userId={}, tenantId={}, path={}", userId, tenantId, path);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (SaTokenException e) {
            log.warn("Auth failed: path={}, error={}", path, e.getMessage());
            return unauthorized(exchange, e.getMessage());
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 返回401响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":401,\"msg\":\"%s\",\"data\":null,\"timestamp\":%d}",
                message, System.currentTimeMillis()
        );
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
