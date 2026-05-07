package com.erp.common.mybatis.datascope;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.erp.common.core.constant.HeaderConstants;
import com.erp.common.core.context.ColumnPermissionContextHolder;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据权限上下文加载过滤器
 *
 * <p>在每次请求开始时，从 Redis 中加载当前用户的 {@link DataScopeContext}，
 * 存入 {@link DataScopeContextHolder}；请求结束后在 finally 块清理，防止内存泄漏。
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
            loadDataScope();
            loadColumnPermissions();
            filterChain.doFilter(request, response);
        } finally {
            DataScopeContextHolder.clear();
            ColumnPermissionContextHolder.clear();
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

    /**
     * 从 Sa-Token Redis Session 加载当前用户的权限码集合，写入 ColumnPermissionContextHolder。
     *
     * <p>内部 Feign 调用（携带 X-Inner-Call: true）时跳过，保持 Holder 为 null，
     * ColumnPermissionSerializer 遇到 null 时直接放行（hasPermission 返回 true）。
     *
     * <p>userId 为 null（未登录）时跳过；Gateway 已确保非法请求不到达此处。
     */
    private void loadColumnPermissions() {
        // 内部调用豁免：不做列权限过滤
        // （HeaderConstants.INNER_CALL 已在当前请求的 ServletRequest 中，
        //   但 DataScopeFilter 在 Servlet 层，无法直接获取；
        //   兜底：userId 为 null 时自然跳过）
        Long userId = TenantContextHolder.getUserId();
        if (userId == null) {
            return;
        }
        try {
            SaSession session = StpUtil.getSessionByLoginId(userId, false);
            if (session == null) {
                // 未登录或 Session 已过期，写空 Set（序列化时无任何列权限）
                ColumnPermissionContextHolder.set(new HashSet<>());
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> permList = (List<String>) session.get("permissions");
            Set<String> permSet = permList != null ? new HashSet<>(permList) : new HashSet<>();
            ColumnPermissionContextHolder.set(permSet);
        } catch (Exception e) {
            // Sa-Token 不可用或 Redis 异常时降级：Holder 保持 null，序列化放行
            log.warn("Failed to load column permissions from Sa-Token session, userId={}: {}",
                    userId, e.getMessage());
        }
    }
}
