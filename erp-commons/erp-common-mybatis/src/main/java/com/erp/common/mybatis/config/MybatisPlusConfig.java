package com.erp.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.erp.common.core.context.TenantContextHolder;
import com.erp.common.mybatis.datascope.DataScopeFilter;
import com.erp.common.mybatis.datascope.ErpDataPermissionHandler;
import com.erp.common.mybatis.handler.ErpMetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

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
     * <p>顺序：多租户 → 数据权限 → 分页 → 乐观锁
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

        // 2. 数据权限（行级 SQL 过滤）
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new ErpDataPermissionHandler()));

        // 3. 分页（MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 4. 乐观锁
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

    /**
     * 数据权限过滤器（仅在 StringRedisTemplate 存在时注册）
     *
     * <p>未引入 Redis 的服务不会注册此 Filter，安全引入 erp-common-mybatis 不报错。
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public DataScopeFilter dataScopeFilter(StringRedisTemplate redisTemplate,
                                           @Autowired(required = false) ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
        return new DataScopeFilter(redisTemplate, mapper);
    }
}
