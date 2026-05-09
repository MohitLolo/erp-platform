package com.erp.common.log.enums;

/**
 * 操作日志结果状态。
 *
 * <ul>
 *   <li>{@link #SUCCESS} 业务方法正常返回</li>
 *   <li>{@link #FAILURE} 业务方法抛出非鉴权类异常</li>
 *   <li>{@link #DENIED} Sa-Token 鉴权失败（{@code NotPermissionException}）</li>
 *   <li>{@link #UNAUTHORIZED} Sa-Token 未登录（{@code NotLoginException}）</li>
 * </ul>
 */
public enum OperationStatus {
    SUCCESS,
    FAILURE,
    DENIED,
    UNAUTHORIZED
}
