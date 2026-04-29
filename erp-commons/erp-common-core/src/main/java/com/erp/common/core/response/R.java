package com.erp.common.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体
 *
 * <p>使用示例：
 * <pre>
 *   return R.ok(data);
 *   return R.fail(ResultCode.UNAUTHORIZED);
 * </pre>
 *
 * @param <T> 响应数据类型
 * @author erp
 * @since 1.0.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态码 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    private R() {}

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功（无数据）
     */
    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功（带数据）
     *
     * @param data 响应数据
     */
    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功（自定义消息）
     *
     * @param message 提示信息
     * @param data    响应数据
     */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败（枚举错误码）
     *
     * @param resultCode 错误码枚举
     */
    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败（自定义消息）
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /**
     * 失败（枚举 + 自定义消息覆盖）
     *
     * @param resultCode 错误码枚举
     * @param message    自定义消息
     */
    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
