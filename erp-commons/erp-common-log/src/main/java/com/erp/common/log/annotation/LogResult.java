package com.erp.common.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法的返回值需要被记入操作日志的 {@code response_data} 字段（脱敏后）。
 *
 * <p>仅方法级。默认<b>不记录</b>返回值（避免业务大对象 / 列表灌满 DB），按需开启。</p>
 *
 * <pre>
 * &#64;SaCheckPermission("system:user:get")
 * &#64;LogResult
 * public R&lt;UserVO&gt; get(Long id) { ... }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogResult {
}
