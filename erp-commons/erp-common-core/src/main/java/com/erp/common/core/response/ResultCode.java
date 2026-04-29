package com.erp.common.core.response;

import lombok.Getter;

/**
 * 统一响应码枚举
 *
 * <p>规范：
 * <ul>
 *   <li>2xx — 成功</li>
 *   <li>4xx — 客户端错误</li>
 *   <li>5xx — 服务端错误</li>
 *   <li>1xxx — 业务错误</li>
 * </ul>
 *
 * @author erp
 * @since 1.0.0
 */
@Getter
public enum ResultCode {

    // ===== 成功 =====
    SUCCESS(200, "操作成功"),

    // ===== 客户端错误 =====
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    // ===== 服务端错误 =====
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    // ===== 业务错误 (1xxx) =====
    BIZ_ERROR(1000, "业务处理失败"),
    TENANT_NOT_FOUND(1001, "租户不存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    ACCOUNT_DISABLED(1004, "账号已被禁用"),
    DUPLICATE_KEY(1005, "数据已存在"),
    DATA_IN_USE(1006, "数据正在使用中，无法删除"),
    STOCK_INSUFFICIENT(1007, "库存不足"),
    AMOUNT_ERROR(1008, "金额计算异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
