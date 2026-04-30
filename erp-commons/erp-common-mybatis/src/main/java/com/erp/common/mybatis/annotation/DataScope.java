package com.erp.common.mybatis.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解，标注在 Mapper 方法上，触发行级数据过滤。
 *
 * <p>示例：
 * <pre>
 * &#64;DataScope(deptAlias = "o", userAlias = "o")
 * List&lt;SaleOrder&gt; selectPageByQuery(@Param("query") SaleOrderQuery query);
 * </pre>
 *
 * <p>当 {@code deptAlias} 不为空且数据权限维度为部门类（scope=2/3/5）时，
 * 拦截器自动注入 {@code {deptAlias}.dept_id IN (...)} 条件。
 * <br>当 {@code userAlias} 不为空且 scope=4 时，注入 {@code {userAlias}.create_by = userId}。
 *
 * @author erp
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * dept_id 字段所在表的别名（如 "t"、"o"），为空则不做部门维度过滤。
     */
    String deptAlias() default "";

    /**
     * create_by 字段所在表的别名，为空则不做本人维度过滤。
     */
    String userAlias() default "";
}
