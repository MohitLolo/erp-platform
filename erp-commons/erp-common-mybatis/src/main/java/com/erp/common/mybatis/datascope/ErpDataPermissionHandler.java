package com.erp.common.mybatis.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.erp.common.mybatis.annotation.DataScope;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ERP 数据权限处理器
 *
 * <p>实现 MyBatis-Plus {@link DataPermissionHandler}，根据 {@link DataScopeContext}
 * 动态拼接 WHERE 数据过滤条件。
 *
 * <p>逻辑：
 * <ul>
 *   <li>scope=1 → 全部数据，不注入任何条件</li>
 *   <li>scope=2/3/5 → {@code {deptAlias}.dept_id IN (deptIds)}</li>
 *   <li>scope=4 → {@code {userAlias}.create_by = userId}</li>
 * </ul>
 *
 * <p>降级规则：
 * <ul>
 *   <li>deptAlias 为空但 scope=2/3/5 → 使用 userAlias.create_by 过滤</li>
 *   <li>DataScopeContext 为 null → 不注入任何条件（内部调用/未登录豁免）</li>
 * </ul>
 *
 * @author erp
 * @since 1.0.0
 */
public class ErpDataPermissionHandler implements DataPermissionHandler {

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        DataScopeContext ctx = DataScopeContextHolder.get();
        // 无上下文（内部调用 / 未登录）→ 不注入
        if (ctx == null) {
            return where;
        }

        // 反射查找对应 Mapper 方法上的 @DataScope 注解
        DataScope annotation = findAnnotation(mappedStatementId);
        if (annotation == null) {
            return where;
        }

        Integer scope = ctx.getDataScope();
        if (scope == null || scope == 1) {
            // 全部数据权限，不过滤
            return where;
        }

        Expression dataScopeExpr = buildExpression(scope, ctx, annotation);
        if (dataScopeExpr == null) {
            return where;
        }

        return where == null ? dataScopeExpr : new AndExpression(where, dataScopeExpr);
    }

    /**
     * 根据 scope 和注解信息构建过滤表达式
     */
    private Expression buildExpression(Integer scope, DataScopeContext ctx, DataScope annotation) {
        String deptAlias = annotation.deptAlias();
        String userAlias = annotation.userAlias();

        switch (scope) {
            case 2:
            case 3:
            case 5: {
                Set<Long> deptIds = ctx.getDeptIds();
                if (deptAlias != null && !deptAlias.isBlank() && deptIds != null && !deptIds.isEmpty()) {
                    return buildInExpression(deptAlias + ".dept_id", deptIds);
                }
                // 降级：deptAlias 为空时使用 create_by 过滤
                if (userAlias != null && !userAlias.isBlank() && ctx.getUserId() != null) {
                    return buildEqualsExpression(userAlias + ".create_by", ctx.getUserId());
                }
                return null;
            }
            case 4: {
                if (userAlias != null && !userAlias.isBlank() && ctx.getUserId() != null) {
                    return buildEqualsExpression(userAlias + ".create_by", ctx.getUserId());
                }
                return null;
            }
            default:
                return null;
        }
    }

    /**
     * 构建 IN 表达式：{column} IN ({values})
     */
    private Expression buildInExpression(String columnName, Set<Long> values) {
        Column column = new Column(columnName);
        List<LongValue> valueExprs = values.stream()
                .map(LongValue::new)
                .collect(Collectors.toList());
        ExpressionList<LongValue> expressionList = new ExpressionList<>(valueExprs);
        InExpression inExpr = new InExpression(column, expressionList);
        return inExpr;
    }

    /**
     * 构建等值表达式：{column} = {value}
     */
    private Expression buildEqualsExpression(String columnName, Long value) {
        Column column = new Column(columnName);
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(new LongValue(value));
        return equalsTo;
    }

    /**
     * 反射查找 Mapper 方法上的 @DataScope 注解
     *
     * @param mappedStatementId 格式：{全限定类名}.{方法名}
     */
    private DataScope findAnnotation(String mappedStatementId) {
        try {
            int lastDot = mappedStatementId.lastIndexOf('.');
            if (lastDot < 0) {
                return null;
            }
            String className = mappedStatementId.substring(0, lastDot);
            String methodName = mappedStatementId.substring(lastDot + 1);
            Class<?> mapperClass = Class.forName(className);
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScope.class)) {
                    return method.getAnnotation(DataScope.class);
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Mapper 接口未找到，忽略
        }
        return null;
    }
}
