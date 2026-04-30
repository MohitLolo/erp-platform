package com.erp.common.mybatis.datascope;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 数据权限上下文持有者（TransmittableThreadLocal，线程池安全）
 *
 * <p>由 {@link DataScopeFilter} 在请求开始时写入，在 finally 块中清理。
 * {@link ErpDataPermissionHandler} 在 MyBatis 拦截阶段读取。
 *
 * @author erp
 * @since 1.0.0
 */
public final class DataScopeContextHolder {

    private static final TransmittableThreadLocal<DataScopeContext> CONTEXT =
            new TransmittableThreadLocal<>();

    private DataScopeContextHolder() {}

    public static void set(DataScopeContext context) {
        CONTEXT.set(context);
    }

    public static DataScopeContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
