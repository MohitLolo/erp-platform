package com.erp.common.auth.util;

import cn.dev33.satoken.stp.StpUtil;
import com.erp.common.core.context.TenantContextHolder;
import com.erp.common.core.exception.BizException;
import com.erp.common.core.response.ResultCode;

/**
 * JWT / Sa-Token 工具类
 *
 * <p>封装常用鉴权操作：获取当前登录用户信息、权限校验等
 *
 * @author erp
 * @since 1.0.0
 */
public final class JwtUtil {

    private JwtUtil() {}

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID
     * @throws BizException 未登录时抛出 UNAUTHORIZED
     */
    public static Long getCurrentUserId() {
        if (!StpUtil.isLogin()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Object loginId = StpUtil.getLoginId();
        return Long.parseLong(loginId.toString());
    }

    /**
     * 获取当前租户 ID（优先从 TTL 上下文，兜底从 Sa-Token Extra 取）
     *
     * @return 租户 ID
     */
    public static String getCurrentTenantId() {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        // 兜底：从 Sa-Token 的 Extra 字段取
        if (StpUtil.isLogin()) {
            Object extra = StpUtil.getExtra("tenantId");
            return extra != null ? extra.toString() : null;
        }
        return null;
    }

    /**
     * 判断当前用户是否拥有指定权限
     *
     * @param permission 权限码，例如 "system:user:add"
     * @return true=有权限
     */
    public static boolean hasPermission(String permission) {
        return StpUtil.isLogin() && StpUtil.hasPermission(permission);
    }

    /**
     * 判断当前用户是否拥有指定角色
     *
     * @param role 角色码，例如 "ADMIN"
     * @return true=有该角色
     */
    public static boolean hasRole(String role) {
        return StpUtil.isLogin() && StpUtil.hasRole(role);
    }

    /**
     * 判断当前请求是否已登录
     *
     * @return true=已登录
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }
}
