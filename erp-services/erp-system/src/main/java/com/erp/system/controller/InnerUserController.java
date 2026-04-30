package com.erp.system.controller;

import com.erp.common.core.response.R;
import com.erp.system.application.service.UserService;
import com.erp.system.domain.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 内部用户接口（供erp-auth调用，不对外暴露）
 */
@RestController
@RequestMapping("/system/inner/user")
@RequiredArgsConstructor
public class InnerUserController {

    private final UserService userService;

    /**
     * 验证用户凭证
     */
    @GetMapping("/verify")
    public SysUser verifyUser(
            @RequestParam("tenantId") String tenantId,
            @RequestParam("username") String username,
            @RequestParam("password") String passwordMd5) {
        return userService.verifyUser(tenantId, username, passwordMd5);
    }
}
