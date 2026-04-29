package com.erp.common.core.exception;

import com.erp.common.core.response.ResultCode;
import lombok.Getter;

/**
 * 业务异常（受控异常，不触发事务回滚）
 *
 * <p>使用示例：
 * <pre>
 *   throw new BizException(ResultCode.STOCK_INSUFFICIENT, "商品[" + skuId + "]库存不足");
 *   throw new BizException(ResultCode.USER_NOT_FOUND);
 * </pre>
 *
 * @author erp
 * @since 1.0.0
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
