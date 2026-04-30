package com.erp.auth.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.erp.auth.dto.LoginRequest;
import com.erp.auth.dto.LoginResponse;
import com.erp.auth.infrastructure.feign.SystemUserFeign;
import com.erp.common.core.exception.BizException;
import com.erp.common.core.response.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;

/**
 * 认证服务
 * 负责：登录/登出/token刷新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SystemUserFeign systemUserFeign;

    /**
     * 用户登录
     * 1. 调用erp-system验证用户凭证
     * 2. 使用Sa-Token生成JWT，将租户信息写入Extra
     * 3. 返回token和用户基本信息
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 调用system服务验证用户名密码
        String passwordMd5 = DigestUtils.md5DigestAsHex(request.getPassword().getBytes());
        SystemUserFeign.UserInfo userInfo = systemUserFeign.verifyUser(
                request.getTenantId(),
                request.getUsername(),
                passwordMd5
        );

        if (userInfo == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (!userInfo.isEnabled()) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号已被禁用");
        }

        // 2. Sa-Token登录，写入JWT Extra信息
        StpUtil.login(userInfo.getUserId(),
                SaLoginModel.create()
                        .setExtra("tenantId", request.getTenantId())
                        .setExtra("userName", userInfo.getUsername())
                        .setExtra("tenantName", userInfo.getTenantName())
        );

        // 3. 写权限到 Redis Session（jwt-mixin 模式下 Session 已创建）
        SaSession session = StpUtil.getSessionByLoginId(userInfo.getUserId());
        List<String> permCodes = systemUserFeign.getUserPermissions(userInfo.getUserId());
        List<String> roleCodes = systemUserFeign.getUserRoles(userInfo.getUserId());
        session.set("permissions", permCodes != null ? permCodes : List.of());
        session.set("roles", roleCodes != null ? roleCodes : List.of());

        String token = StpUtil.getTokenValue();
        log.info("User login success: userId={}, tenantId={}, permissions={}",
                userInfo.getUserId(), request.getTenantId(), permCodes != null ? permCodes.size() : 0);

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(StpUtil.getTokenTimeout())
                .userId(userInfo.getUserId())
                .username(userInfo.getUsername())
                .tenantId(request.getTenantId())
                .tenantName(userInfo.getTenantName())
                .build();
    }

    /**
     * 用户登出
     */
    public void logout() {
        long userId = StpUtil.getLoginIdAsLong();
        StpUtil.logout();
        log.info("User logout: userId={}", userId);
    }

    /**
     * 刷新token（活跃超时续期）
     */
    public LoginResponse refreshToken() {
        StpUtil.checkLogin();
        // 续期活跃超时
        StpUtil.updateLastActiveToNow();

        long userId = StpUtil.getLoginIdAsLong();
        String tenantId = StpUtil.getExtra("tenantId").toString();
        String userName = StpUtil.getExtra("userName").toString();
        String tenantName = StpUtil.getExtra("tenantName") != null
                ? StpUtil.getExtra("tenantName").toString() : "";

        return LoginResponse.builder()
                .token(StpUtil.getTokenValue())
                .tokenType("Bearer")
                .expiresIn(StpUtil.getTokenTimeout())
                .userId(userId)
                .username(userName)
                .tenantId(tenantId)
                .tenantName(tenantName)
                .build();
    }
}
