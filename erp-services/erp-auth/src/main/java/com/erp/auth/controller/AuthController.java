package com.erp.auth.controller;

import com.erp.auth.dto.LoginRequest;
import com.erp.auth.dto.LoginResponse;
import com.erp.auth.service.AuthService;
import com.erp.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /**
     * 用户登出
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /**
     * 刷新Token
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public R<LoginResponse> refresh() {
        return R.ok(authService.refreshToken());
    }
}
