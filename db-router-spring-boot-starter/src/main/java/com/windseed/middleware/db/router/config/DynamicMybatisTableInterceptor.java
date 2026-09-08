package com.windseed.middleware.db.router.config;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.Properties;

@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class DynamicMybatisTableInterceptor implements Interceptor {

    private static final String TABLE_USER_STRATEGY_EXPORT = "user_strategy_export";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (DBContextHolder.getTBKey() != null) {
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
            BoundSql boundSql = statementHandler.getBoundSql();
            String sql = boundSql.getSql();
            if (sql.contains(TABLE_USER_STRATEGY_EXPORT)) {
                metaObject.setValue("delegate.boundSql.sql", sql.replaceAll("\\b" + TABLE_USER_STRATEGY_EXPORT + "\\b", TABLE_USER_STRATEGY_EXPORT + "_" + DBContextHolder.getTBKey()));
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

}
