package com.erp.common.log.resolver;

/**
 * 权限名解析器接口。
 *
 * <p>切面仅依赖本接口，具体实现（Caffeine+Redis / 纯 Redis / Feign 兜底）由自动配置选择。
 * 返回 null 表示解析失败，切面会把 {@code permission_name} 留空但日志照写。</p>
 */
public interface PermissionNameResolver {

    /**
     * 根据权限编码解析元信息。
     *
     * @param permissionCode 权限编码（{@code @SaCheckPermission} 的 value）
     * @return 元信息；未命中或异常时返回 null，<b>禁止抛异常到切面层</b>
     */
    PermissionMeta resolve(String permissionCode);
}
