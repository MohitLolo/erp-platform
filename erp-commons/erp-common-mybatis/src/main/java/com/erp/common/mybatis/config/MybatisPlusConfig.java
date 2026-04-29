package com.erp.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.erp.common.core.context.TenantContextHolder;
import com.erp.common.mybatis.handler.ErpMetaObjectHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置
 *
 * <p>提供：
 * <ul>
 *   <li>分页插件（MySQL）</li>
 *   <li>乐观锁插件</li>
 *   <li>多租户（Schema 隔离）</li>
 *   <li>字段自动填充</li>
 * </ul>
 *
 * @author erp
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链
     *
     * <p>顺序：多租户 → 分页 → 乐观锁
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户：Schema 级别隔离（修改 SQL 的 schema 前缀）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                    @Override
                    public Expression getTenantId() {
                        String schema = TenantContextHolder.getTenantSchema();
                        return new StringValue(schema != null ? schema : "erp_default");
                    }

                    /**
                     * 是否忽略该表的多租户处理
                     * sys_tenant 等公共表不需要隔离
                     */
                    @Override
                    public boolean ignoreTable(String tableName) {
                        return "sys_tenant".equalsIgnoreCase(tableName)
                                || tableName.startsWith("seata_");
                    }
                }
        ));

        // 2. 分页（MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 3. 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 字段自动填充处理器
     */
    @Bean
    public MetaObjectHandler erpMetaObjectHandler() {
        return new ErpMetaObjectHandler();
    }
}
