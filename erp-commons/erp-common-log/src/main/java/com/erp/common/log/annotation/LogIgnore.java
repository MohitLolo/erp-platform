package com.erp.common.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要被操作日志切面跳过的方法（即使带 {@code @SaCheckPermission} 也不会被记录）。
 *
 * <p>仅方法级。常见用途：高频读接口（list/page）、心跳/导出等不希望写入日志的端点。</p>
 *
 * <pre>
 * &#64;SaCheckPermission("system:user:list")
 * &#64;LogIgnore  // 列表接口高频，不记日志
 * public R&lt;Page&lt;UserVO&gt;&gt; list(PageQuery q) { ... }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogIgnore {
}
