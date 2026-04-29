package com.erp.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.erp.common.core.context.TenantContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 *
 * <p>自动填充字段：createTime / updateTime / createBy / updateBy
 * <p>实体类中需要使用 {@code @TableField(fill = FieldFill.INSERT)} 或 {@code INSERT_UPDATE} 注解
 *
 * @author erp
 * @since 1.0.0
 */
@Component
public class ErpMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = TenantContextHolder.getUserId();

        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);

        if (userId != null) {
            this.strictInsertFill(metaObject, "createBy", Long.class, userId);
            this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = TenantContextHolder.getUserId();

        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
    }
}
