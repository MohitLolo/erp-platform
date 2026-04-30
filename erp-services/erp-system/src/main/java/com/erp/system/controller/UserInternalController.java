package com.erp.system.controller;

import com.erp.system.application.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部服务调用接口（不对外暴露，仅供 erp-auth 通过 Feign 调用）
 *
 * <p>此接口不加 @SaCheckPermission，由 Gateway 路由隔离保证安全。
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    /**
     * 查询用户权限码列表（permType=2,3，排除菜单权限）
     */
    @GetMapping("/{userId}/permissions")
    public List<String> getUserPermissions(@PathVariable Long userId) {
        return userService.getUserPermissions(userId);
    }

    /**
     * 查询用户角色码列表
     */
    @GetMapping("/{userId}/roles")
    public List<String> getUserRoles(@PathVariable Long userId) {
        return userService.getUserRoles(userId);
    }
}
