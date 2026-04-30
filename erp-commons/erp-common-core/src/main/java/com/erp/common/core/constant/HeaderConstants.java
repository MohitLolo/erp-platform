package com.erp.common.core.constant;

/**
 * HTTP 请求头常量
 *
 * <p>定义服务间传递的标准请求头名称，用于 TTL 上下文传播
 *
 * @author erp
 * @since 1.0.0
 */
public final class HeaderConstants {

    private HeaderConstants() {}

    /** 租户 ID */
    public static final String TENANT_ID = "X-Tenant-Id";

    /** 用户 ID */
    public static final String USER_ID = "X-User-Id";

    /** 用户名 */
    public static final String USER_NAME = "X-User-Name";

    /** 链路追踪 ID（SkyWalking 注入，此处仅定义常量） */
    public static final String TRACE_ID = "X-Trace-Id";

    /** 灰度路由标签 */
    public static final String GRAY_TAG = "X-Gray-Tag";

    /** 内部服务调用标识，值为 "true" 时跳过数据权限过滤 */
    public static final String INNER_CALL = "X-Inner-Call";
}
