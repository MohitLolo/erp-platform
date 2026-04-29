package com.erp.common.log.filter;

import com.erp.common.core.constant.HeaderConstants;
import com.erp.common.core.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * MDC 上下文过滤器
 *
 * <p>职责：
 * <ol>
 *   <li>从请求头中读取 tenantId / userId / userName，填充 TTL 上下文</li>
 *   <li>生成或传递 traceId，写入 MDC 供日志使用</li>
 *   <li>请求结束后清理 TTL 上下文和 MDC，防止内存泄漏</li>
 * </ol>
 *
 * @author erp
 * @since 1.0.0
 */
@Component
@Order(Integer.MIN_VALUE)
public class MdcContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 1. 读取 Gateway 下发的上下文请求头
            String tenantId = request.getHeader(HeaderConstants.TENANT_ID);
            String userIdStr = request.getHeader(HeaderConstants.USER_ID);
            String userName = request.getHeader(HeaderConstants.USER_NAME);

            // 2. 填充 TTL 上下文
            if (StringUtils.hasText(tenantId)) {
                TenantContextHolder.setTenantId(tenantId);
            }
            if (StringUtils.hasText(userIdStr)) {
                try {
                    TenantContextHolder.setUserId(Long.parseLong(userIdStr));
                } catch (NumberFormatException ignored) {
                    // 非数字 userId 忽略
                }
            }
            if (StringUtils.hasText(userName)) {
                TenantContextHolder.setUserName(userName);
            }

            // 3. 生成 traceId（优先使用 SkyWalking 注入的，否则自生成）
            String traceId = request.getHeader(HeaderConstants.TRACE_ID);
            if (!StringUtils.hasText(traceId)) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }

            // 4. 写入 MDC（Logback 日志模式中引用 %X{traceId} 等）
            MDC.put("traceId", traceId);
            MDC.put("tenantId", StringUtils.hasText(tenantId) ? tenantId : "-");
            MDC.put("userId", StringUtils.hasText(userIdStr) ? userIdStr : "-");

            filterChain.doFilter(request, response);
        } finally {
            // 5. 清理 TTL 上下文 + MDC，防止内存泄漏
            TenantContextHolder.clear();
            MDC.clear();
        }
    }
}
