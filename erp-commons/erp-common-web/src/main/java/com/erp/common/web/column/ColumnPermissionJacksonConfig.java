package com.erp.common.web.column;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 列级权限 Jackson 自动配置
 *
 * <p>将 {@link ColumnPermissionBeanSerializerModifier} 注册进 Spring 管理的
 * {@link ObjectMapper}，使其对所有通过 Spring MVC 返回的 JSON 响应生效。
 *
 * <p>通过 {@code AutoConfiguration.imports} 自动加载，引入 {@code erp-common-web}
 * 的服务无需任何额外配置。
 *
 * @author erp
 * @since 1.0.0
 */
@AutoConfiguration
public class ColumnPermissionJacksonConfig {

    /**
     * 注册列级权限序列化修饰器
     *
     * <p>Jackson 在构建每个类的序列化器时（启动期，一次性）调用
     * {@code BeanSerializerModifier.changeProperties()}，对标有
     * {@code @ColumnPermission} 的字段替换为包装序列化器。
     */
    @Bean
    public ColumnPermissionBeanSerializerModifier columnPermissionBeanSerializerModifier(
            ObjectMapper objectMapper) {
        ColumnPermissionBeanSerializerModifier modifier = new ColumnPermissionBeanSerializerModifier();
        objectMapper.registerModule(
                new SimpleModule().setSerializerModifier(modifier)
        );
        return modifier;
    }
}
