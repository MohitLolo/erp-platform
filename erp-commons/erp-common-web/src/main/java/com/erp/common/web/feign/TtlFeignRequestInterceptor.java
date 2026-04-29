package com.erp.common.web.feign;

import com.erp.common.core.constant.HeaderConstants;
import com.erp.common.core.context.TenantContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器 — TTL 上下文传播
 *
 * <p>在每次 Feign 调用前，将 TTL 上下文中的 tenantId / userId / userName
 * 注入到请求头，确保被调用服务能正确恢复上下文。
 *
 * <p>替代原有的匿名 {@code RequestInterceptor} Bean，
 * 配合 {@code MdcContextFilter} 实现完整的上下文链路传播。
 *
 * @author erp
 * @since 1.0.0
 */
@Component
public class TtlFeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String tenantId = TenantContextHolder.getTenantId();
        Long userId = TenantContextHolder.getUserId();
        String userName = TenantContextHolder.getUserName();

        if (tenantId != null && !tenantId.isBlank()) {
            template.header(HeaderConstants.TENANT_ID, tenantId);
        }
        if (userId != null) {
            template.header(HeaderConstants.USER_ID, userId.toString());
        }
        if (userName != null && !userName.isBlank()) {
            template.header(HeaderConstants.USER_NAME, userName);
        }
    }
}
