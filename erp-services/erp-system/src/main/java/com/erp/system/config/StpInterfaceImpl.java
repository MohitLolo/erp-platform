package com.erp.system.config;

import cn.dev33.satoken.stp.StpInterface;
import com.erp.system.infrastructure.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源实现
 * Sa-Token在校验权限时会调用此接口获取当前登录用户的角色和权限列表
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysPermissionMapper permissionMapper;

    /**
     * 返回当前用户的权限码集合
     * Sa-Token会缓存结果，无需手动缓存
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        return permissionMapper.findPermCodesByUserId(userId);
    }

    /**
     * 返回当前用户的角色码集合
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        return permissionMapper.findRoleCodesByUserId(userId);
    }
}
