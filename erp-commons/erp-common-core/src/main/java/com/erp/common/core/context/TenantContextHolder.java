package com.erp.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.alibaba.ttl.threadpool.TtlExecutors;

import java.util.concurrent.ExecutorService;

/**
 * 租户 & 用户上下文持有者（基于 TransmittableThreadLocal，线程池安全）
 *
 * <p>使用方式：
 * <pre>
 *   TenantContextHolder.setTenantId("tenant_001");
 *   String id = TenantContextHolder.getTenantId();
 *   TenantContextHolder.clear(); // 请求结束后必须清理
 * </pre>
 *
 * @author erp
 * @since 1.0.0
 */
public final class TenantContextHolder {

    private static final TransmittableThreadLocal<String> TENANT_ID = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<String> TENANT_SCHEMA = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<Long>   USER_ID = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<String> USER_NAME = new TransmittableThreadLocal<>();

    private TenantContextHolder() {}

    // ---- tenantId ----

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    // ---- tenantSchema ----

    public static void setTenantSchema(String schema) {
        TENANT_SCHEMA.set(schema);
    }

    public static String getTenantSchema() {
        return TENANT_SCHEMA.get();
    }

    // ---- userId ----

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    // ---- userName ----

    public static void setUserName(String userName) {
        USER_NAME.set(userName);
    }

    public static String getUserName() {
        return USER_NAME.get();
    }

    /**
     * 清理当前线程所有上下文，防止内存泄漏
     * 应在请求处理完成后（finally 块）调用
     */
    public static void clear() {
        TENANT_ID.remove();
        TENANT_SCHEMA.remove();
        USER_ID.remove();
        USER_NAME.remove();
    }

    /**
     * 用 TTL 包装线程池，确保上下文正确传递到子线程
     *
     * @param executorService 原始线程池
     * @return TTL 包装后的线程池
     */
    public static ExecutorService wrapExecutor(ExecutorService executorService) {
        return TtlExecutors.getTtlExecutorService(executorService);
    }
}
