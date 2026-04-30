package com.erp.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.erp.common.core.response.R;
import com.erp.common.core.context.TenantContextHolder;
import com.erp.system.application.service.UserService;
import com.erp.system.domain.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口（对外暴露，需鉴权）
 */
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final UserService userService;

    /**
     * 获取用户列表
     * 需要权限：system:user:list
     */
    @GetMapping("/list")
    @SaCheckPermission("system:user:list")
    public R<List<SysUser>> list() {
        String tenantId = TenantContextHolder.getTenantId();
        return R.ok(userService.listByTenant(tenantId));
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{userId}")
    @SaCheckPermission("system:user:query")
    public R<SysUser> getById(@PathVariable Long userId) {
        return R.ok(userService.getById(userId));
    }
}
