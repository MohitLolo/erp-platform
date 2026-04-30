package com.erp.common.mybatis.datascope;

import com.erp.common.core.constant.HeaderConstants;
import com.erp.common.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 数据权限上下文加载过滤器
 *
 * <p>在每次请求开始时，从 Redis 中加载当前用户的 {@link DataScopeContext}，
 * 存入 {@link DataScopeContextHolder}；请求结束后在 finally 块清理，防止内存泄漏。
 *
 * <p>内部 Feign 调用豁免：检测到 {@code X-Inner-Call: true} 请求头时，跳过加载。
 * 此时 DataScopeContextHolder 保持 null，{@link ErpDataPermissionHandler}
 * 对 null 上下文不注入任何条件。
 *
 * <p>Redis 缓存 Key 格式：{@code data:scope:{tenantId}:{userId}}
 *
 * @author erp
 * @since 1.0.0
 */
@Order(-100)
public class DataScopeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DataScopeFilter.class);

    private static final String REDIS_KEY_PREFIX = "data:scope:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DataScopeFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String innerCall = request.getHeader(HeaderConstants.INNER_CALL);
            if ("true".equalsIgnoreCase(innerCall)) {
                // 内部服务调用，跳过数据权限加载
                filterChain.doFilter(request, response);
                return;
            }

            loadDataScope();
            filterChain.doFilter(request, response);
        } finally {
            DataScopeContextHolder.clear();
        }
    }

    private void loadDataScope() {
        String tenantId = TenantContextHolder.getTenantId();
        Long userId = TenantContextHolder.getUserId();

        if (tenantId == null || userId == null) {
            return;
        }

        String key = REDIS_KEY_PREFIX + tenantId + ":" + userId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                DataScopeContext ctx = objectMapper.readValue(json, DataScopeContext.class);
                DataScopeContextHolder.set(ctx);
            }
        } catch (Exception e) {
            log.warn("Failed to load DataScopeContext from Redis, key={}: {}", key, e.getMessage());
        }
    }
}
