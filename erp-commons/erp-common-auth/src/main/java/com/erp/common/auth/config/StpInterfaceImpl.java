package com.erp.common.auth.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限接口实现（共享版）
 *
 * <p>从 Redis Session 读取权限列表，Session 在登录时由 erp-auth 写入。
 * 注册为 Spring Bean 后，所有引入 erp-common-auth 的服务中
 * {@code @SaCheckPermission} / {@code @SaCheckRole} 自动生效。
 *
 * <p>依赖 jwt-mixin 模式，不支持 jwt-default（无 Session）。
 *
 * @see com.erp.auth.service.AuthService
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return List.of();
        }
        List<String> perms = session.get("permissions");
        return perms != null ? perms : List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return List.of();
        }
        List<String> roles = session.get("roles");
        return roles != null ? roles : List.of();
    }
}
