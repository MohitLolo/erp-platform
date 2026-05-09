package com.erp.common.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要参与操作日志记录的入参。
 *
 * <p>支持两种用法：</p>
 * <ul>
 *   <li><b>方法级</b>：标注在方法上，记录方法的<i>所有</i>非排除参数（参考 §6.6 自动跳过规则）。</li>
 *   <li><b>参数级</b>：标注在单个参数上，仅记录该参数；同方法多个参数都标注则全部记录。</li>
 * </ul>
 *
 * <p>同时存在方法级与参数级时，参数级优先。可通过 {@link #value()} 在日志中改写字段名（默认使用方法形参名，
 * 需要编译期 {@code -parameters} 标志启用）。</p>
 *
 * <pre>
 * &#64;SaCheckPermission("system:user:create")
 * &#64;LogParam
 * public R&lt;Long&gt; create(&#64;Valid UserCreateDTO dto) { ... }
 *
 * // 仅记录 userId
 * &#64;SaCheckPermission("system:user:disable")
 * public R&lt;Void&gt; disable(&#64;LogParam("uid") Long userId, String reason) { ... }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface LogParam {

    /**
     * 自定义字段名。空字符串表示使用形参名（需 {@code javac -parameters}）。
     * 仅参数级生效；方法级保留 Map 默认结构（key=形参名）。
     */
    String value() default "";
}
