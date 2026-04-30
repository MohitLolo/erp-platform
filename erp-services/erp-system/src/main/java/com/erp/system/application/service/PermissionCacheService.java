package com.erp.system.application.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.erp.system.infrastructure.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限缓存刷新服务
 *
 * <p>在角色/权限变更后主动刷新用户的 Sa-Token Redis Session，
 * 使权限变更实时生效，无需等待 Token 过期。
 *
 * <p>调用时机：
 * <ul>
 *   <li>给用户分配/移除角色</li>
 *   <li>修改角色的权限集合（需批量刷新该角色下所有用户）</li>
 *   <li>禁用用户账号（调用 kickOut）</li>
 * </ul>
 *
 * @author erp
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final SysPermissionMapper permissionMapper;

    /**
     * 刷新指定用户的权限 Session
     *
     * <p>如果用户未登录（Session 不存在），则跳过，不产生空 Session。
     *
     * @param userId 用户 ID
     */
    public void refresh(Long userId) {
        SaSession session = StpUtil.getSessionByLoginId(userId, false);
        if (session == null) {
            log.debug("User {} is not logged in, skip permission cache refresh", userId);
            return;
        }
        List<String> permCodes = permissionMapper.findPermCodesByUserId(userId);
        List<String> roleCodes = permissionMapper.findRoleCodesByUserId(userId);
        session.set("permissions", permCodes);
        session.set("roles", roleCodes);
        log.info("Permission cache refreshed for userId={}, permCount={}", userId, permCodes.size());
    }

    /**
     * 踢出用户（清除 Token + Session）
     *
     * <p>用于禁用账号、强制下线等场景。
     *
     * @param userId 用户 ID
     */
    public void kickOut(Long userId) {
        StpUtil.logout(userId);
        log.info("User kicked out: userId={}", userId);
    }
}
