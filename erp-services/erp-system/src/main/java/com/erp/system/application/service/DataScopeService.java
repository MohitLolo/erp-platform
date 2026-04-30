package com.erp.system.application.service;

import com.erp.common.mybatis.datascope.DataScopeContext;
import com.erp.common.mybatis.datascope.DataScopeLevel;
import com.erp.system.domain.entity.SysDept;
import com.erp.system.domain.entity.SysRole;
import com.erp.system.domain.entity.SysUser;
import com.erp.system.infrastructure.mapper.SysDeptMapper;
import com.erp.system.infrastructure.mapper.SysRoleDeptMapper;
import com.erp.system.infrastructure.mapper.SysRoleMapper;
import com.erp.system.infrastructure.mapper.SysUserDeptMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 数据权限计算服务
 *
 * <p>职责：
 * <ol>
 *   <li>查询用户角色，取最小（最宽松）dataScope</li>
 *   <li>按 scope 规则计算可访问 deptIds</li>
 *   <li>将 {@link DataScopeContext} 序列化写入 Redis（TTL 5 分钟）</li>
 * </ol>
 *
 * <p>Redis Key 格式：{@code data:scope:{tenantId}:{userId}}
 *
 * <p>调用时机：用户登录成功后、角色/部门变更后
 *
 * @author erp
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private static final String REDIS_KEY_PREFIX = "data:scope:";
    private static final long TTL_MINUTES = 5L;

    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserDeptMapper userDeptMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 根据用户信息计算数据权限上下文并写入 Redis
     *
     * @param user 已登录用户（需含 tenantId, id, deptId）
     */
    public void buildAndCache(SysUser user) {
        List<SysRole> roles = roleMapper.findRolesByUserId(user.getId());
        if (roles == null || roles.isEmpty()) {
            // 没有角色，默认仅本人数据
            cacheContext(user.getTenantId(), user.getId(), buildContext(DataScopeLevel.SELF, user, List.of()));
            return;
        }

        // 取最小 scope（数值越小权限越宽）
        int minScope = roles.stream()
                .filter(r -> r.getDataScope() != null)
                .mapToInt(SysRole::getDataScope)
                .min()
                .orElse(DataScopeLevel.SELF.code());

        DataScopeLevel scopeLevel = DataScopeLevel.fromCode(minScope);
        if (scopeLevel == null) {
            scopeLevel = DataScopeLevel.SELF;
        }
        DataScopeContext ctx = buildContext(scopeLevel, user, roles);
        cacheContext(user.getTenantId(), user.getId(), ctx);
    }

    /**
     * 主动清除用户的数据权限缓存（角色/部门变更时调用）
     */
    public void evict(String tenantId, Long userId) {
        String key = REDIS_KEY_PREFIX + tenantId + ":" + userId;
        redisTemplate.delete(key);
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private DataScopeContext buildContext(DataScopeLevel scopeLevel, SysUser user, List<SysRole> roles) {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setDataScope(scopeLevel.code());
        ctx.setUserId(user.getId());
        ctx.setPrimaryDeptId(user.getDeptId());

        Set<Long> deptIds = new HashSet<>();

        switch (scopeLevel) {
            case ALL:
                // 全部数据，deptIds 为空（handler 不注入条件）
                break;
            case DEPT:
                // 本部门
                if (user.getDeptId() != null) {
                    deptIds.add(user.getDeptId());
                }
                break;
            case DEPT_AND_CHILD:
                // 本部门及下级
                if (user.getDeptId() != null) {
                    List<Long> subIds = deptMapper.findSubDeptIds(user.getDeptId());
                    deptIds.addAll(subIds);
                }
                break;
            case SELF:
                // 仅本人，deptIds 无意义，handler 用 userId 过滤
                break;
            case CUSTOM_DEPT:
                // 自定义：union 所有角色中 scope=5 的 role_dept
                for (SysRole role : roles) {
                    if (DataScopeLevel.fromCode(role.getDataScope()) == DataScopeLevel.CUSTOM_DEPT) {
                        List<Long> roleDeptIds = roleDeptMapper.findDeptIdsByRoleId(role.getId());
                        deptIds.addAll(roleDeptIds);
                    }
                }
                break;
            default:
                break;
        }

        ctx.setDeptIds(deptIds);
        return ctx;
    }

    private void cacheContext(String tenantId, Long userId, DataScopeContext ctx) {
        String key = REDIS_KEY_PREFIX + tenantId + ":" + userId;
        try {
            String json = objectMapper.writeValueAsString(ctx);
            redisTemplate.opsForValue().set(key, json, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DataScopeContext for key={}: {}", key, e.getMessage());
        }
    }
}
