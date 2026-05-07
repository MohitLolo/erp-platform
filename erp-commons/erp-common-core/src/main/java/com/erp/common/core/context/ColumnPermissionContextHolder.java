package com.erp.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Set;

/**
 * 列级权限上下文持有者（TransmittableThreadLocal，线程池安全）
 *
 * <p>由 {@code DataScopeFilter} 在请求开始时从 Sa-Token Redis Session 填充，
 * 在请求结束的 finally 块清理。Jackson 序列化阶段由
 * {@code ColumnPermissionSerializer} 读取，进行字段级权限判断。
 *
 * <p>为 null 表示当前请求不需要列权限过滤（内部 Feign 调用或未登录请求在
 * Gateway 已被拦截）；空 Set 表示已登录但无任何列权限。
 *
 * @author erp
 * @since 1.0.0
 */
public final class ColumnPermissionContextHolder {

    private static final TransmittableThreadLocal<Set<String>> HOLDER =
            new TransmittableThreadLocal<>();

    private ColumnPermissionContextHolder() {}

    /**
     * 写入当前用户的列权限码集合
     *
     * @param permissions 权限码 Set（来自 Sa-Token Redis Session 的 "permissions" key）
     */
    public static void set(Set<String> permissions) {
        HOLDER.set(permissions);
    }

    /**
     * 获取当前用户的列权限码集合
     *
     * @return 权限码 Set；null 表示未设置（内部调用场景）
     */
    public static Set<String> get() {
        return HOLDER.get();
    }

    /**
     * 判断当前用户是否拥有指定列权限码
     *
     * <p>Holder 为 null 时（内部调用）返回 true，直接放行。
     *
     * @param permCode 权限码，对应 sys_permission.perm_code（perm_type=4）
     * @return true 表示有权限（或内部调用）
     */
    public static boolean hasPermission(String permCode) {
        Set<String> perms = HOLDER.get();
        if (perms == null) {
            // 内部 Feign 调用或 DataScopeFilter 未运行的场景，直接放行
            return true;
        }
        return perms.contains(permCode);
    }

    /**
     * 清理当前线程上下文，防止内存泄漏
     * 必须在请求结束的 finally 块中调用
     */
    public static void clear() {
        HOLDER.remove();
    }
}
