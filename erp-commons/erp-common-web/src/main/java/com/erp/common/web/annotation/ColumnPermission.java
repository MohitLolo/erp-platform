package com.erp.common.web.annotation;

import java.lang.annotation.*;

/**
 * 列级数据权限注解
 *
 * <p>标注在 VO / DTO 字段上，声明查看该字段所需的权限码。
 * 对应 {@code sys_permission.perm_code}（{@code perm_type=4}）。
 *
 * <p>示例：
 * <pre>
 *   public class PurchaseOrderVO {
 *
 *       private String orderNo;   // 无注解，所有人可见
 *
 *       {@literal @}ColumnPermission("purchase:order:view_cost")
 *       private BigDecimal unitCost;   // 无权限时返回 null
 *   }
 * </pre>
 *
 * <p>权限控制由 {@code ColumnPermissionBeanSerializerModifier} 在 Jackson
 * 序列化阶段自动介入，业务代码无感知。
 *
 * @author erp
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ColumnPermission {

    /**
     * 查看该字段所需的权限码
     * 对应 {@code sys_permission.perm_code}，{@code perm_type=4}
     */
    String value();
}
