package com.erp.common.web.column;

import com.erp.common.web.annotation.ColumnPermission;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * 列级权限 Bean 序列化修饰器
 *
 * <p>在 Jackson 构建 Bean Serializer 时介入（项目启动时执行一次，之后缓存）：
 * 遍历所有属性，将标有 {@code @ColumnPermission} 的字段替换为
 * {@link ColumnPermissionSerializer} 包装版。
 *
 * <p>对所有 VO / DTO / Entity 自动生效，包括嵌套对象和 List 中的元素。
 *
 * @author erp
 * @since 1.0.0
 */
public class ColumnPermissionBeanSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                      BeanDescription beanDesc,
                                                      List<BeanPropertyWriter> beanProperties) {
        for (int i = 0; i < beanProperties.size(); i++) {
            BeanPropertyWriter writer = beanProperties.get(i);
            ColumnPermission annotation = writer.getAnnotation(ColumnPermission.class);
            if (annotation != null) {
                // 用权限序列化器包装原始序列化器
                @SuppressWarnings("unchecked")
                JsonSerializer<Object> original = (JsonSerializer<Object>) writer.getSerializer();
                ColumnPermissionSerializer wrapped =
                        new ColumnPermissionSerializer(original, annotation.value());
                writer.assignSerializer(wrapped);
            }
        }
        return beanProperties;
    }
}
