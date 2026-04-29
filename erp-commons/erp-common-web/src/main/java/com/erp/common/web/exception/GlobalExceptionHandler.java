package com.erp.common.web.exception;

import com.erp.common.core.exception.BizException;
import com.erp.common.core.response.R;
import com.erp.common.core.response.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>处理顺序（优先级从高到低）：
 * <ol>
 *   <li>{@link BizException} — 业务异常，返回对应业务错误码</li>
 *   <li>{@link MethodArgumentNotValidException} / {@link BindException} — 参数校验异常</li>
 *   <li>{@link Exception} — 兜底，返回 500</li>
 * </ol>
 *
 * @author erp
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 统一响应体
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBizException(BizException e) {
        log.warn("BizException: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid / @Validated 触发）
     *
     * @param e 参数校验异常
     * @return 统一响应体（400）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return R.fail(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 处理表单绑定异常
     *
     * @param e 绑定异常
     * @return 统一响应体（400）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Bind failed: {}", message);
        return R.fail(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 兜底处理所有未捕获异常
     *
     * @param e 异常
     * @return 统一响应体（500）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return R.fail(ResultCode.INTERNAL_SERVER_ERROR);
    }
}
