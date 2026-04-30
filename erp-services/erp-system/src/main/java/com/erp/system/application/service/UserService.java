package com.erp.system.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.core.exception.BizException;
import com.erp.common.core.response.ResultCode;
import com.erp.system.domain.entity.SysTenant;
import com.erp.system.domain.entity.SysUser;
import com.erp.system.infrastructure.mapper.SysPermissionMapper;
import com.erp.system.infrastructure.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;

    /**
     * 验证用户凭证（供erp-auth内部调用）
     */
    public SysUser verifyUser(String tenantId, String username, String passwordMd5) {
        SysUser user = userMapper.findByTenantAndUsername(tenantId, username);
        if (user == null) {
            return null;
        }
        if (!passwordMd5.equalsIgnoreCase(user.getPassword())) {
            return null;
        }
        if (user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    /**
     * 根据ID查询用户
     */
    public SysUser getById(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 获取用户权限列表
     */
    public List<String> getUserPermissions(Long userId) {
        return permissionMapper.findPermCodesByUserId(userId);
    }

    /**
     * 获取用户角色列表
     */
    public List<String> getUserRoles(Long userId) {
        return permissionMapper.findRoleCodesByUserId(userId);
    }

    /**
     * 分页查询用户列表（租户隔离）
     */
    public List<SysUser> listByTenant(String tenantId) {
        return userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .orderByDesc(SysUser::getCreateTime)
        );
    }
}
