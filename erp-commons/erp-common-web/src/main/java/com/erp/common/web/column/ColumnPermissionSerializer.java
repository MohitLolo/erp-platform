package com.erp.common.web.column;

import com.erp.common.core.context.ColumnPermissionContextHolder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 列级权限序列化器
 *
 * <p>包装原始 {@link JsonSerializer}：
 * <ul>
 *   <li>当前用户拥有权限码 → 委托原始 Serializer 正常输出字段值</li>
 *   <li>当前用户无权限码 → 写出 {@code null}（key 保留，value 置空）</li>
 *   <li>{@code ColumnPermissionContextHolder} 为 null（内部 Feign 调用）→ 直接放行</li>
 * </ul>
 *
 * <p>由 {@link ColumnPermissionBeanSerializerModifier} 在 Jackson 初始化时
 * 自动包装标有 {@code @ColumnPermission} 注解的字段，业务代码无需感知。
 *
 * @author erp
 * @since 1.0.0
 */
public class ColumnPermissionSerializer extends JsonSerializer<Object> {

    /** 原始序列化器（字段有权限时委托它处理） */
    private final JsonSerializer<Object> delegate;

    /** 对应 sys_permission.perm_code（perm_type=4） */
    private final String permCode;

    public ColumnPermissionSerializer(JsonSerializer<Object> delegate, String permCode) {
        this.delegate = delegate;
        this.permCode = permCode;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {

        if (ColumnPermissionContextHolder.hasPermission(permCode)) {
            // 有权限（含内部调用）→ 正常序列化
            delegate.serialize(value, gen, serializers);
        } else {
            // 无权限 → 写 null，key 保留便于前端渲染占位符
            gen.writeNull();
        }
    }
}
