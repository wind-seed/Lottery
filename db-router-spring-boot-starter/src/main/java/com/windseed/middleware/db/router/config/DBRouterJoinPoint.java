package com.windseed.middleware.db.router.config;

import com.windseed.middleware.db.router.annotation.DBRouter;
import com.windseed.middleware.db.router.annotation.DBRouterStrategy;
import com.windseed.middleware.db.router.strategy.IDBRouterStrategy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Aspect
public class DBRouterJoinPoint {

    private final IDBRouterStrategy dbRouterStrategy;
    private final String defaultRouterKey;

    public DBRouterJoinPoint(IDBRouterStrategy dbRouterStrategy, String defaultRouterKey) {
        this.dbRouterStrategy = dbRouterStrategy;
        this.defaultRouterKey = defaultRouterKey;
    }

    @Around("@annotation(dbRouter)")
    public Object doRouter(ProceedingJoinPoint jp, DBRouter dbRouter) throws Throwable {
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        DBRouterStrategy strategy = method.getDeclaringClass().getAnnotation(DBRouterStrategy.class);
        boolean splitTable = strategy != null && strategy.splitTable();

        boolean routedHere = DBContextHolder.getDBKey() == null;
        try {
            if (routedHere) {
                String key = dbRouter.key().isEmpty() ? defaultRouterKey : dbRouter.key();
                dbRouterStrategy.doRouter(resolveRouterValue(key, jp.getArgs()));
            }
            if (splitTable) {
                DBContextHolder.setSplitTable(true);
            }
            return jp.proceed();
        } finally {
            if (routedHere) {
                dbRouterStrategy.clear();
            }
        }
    }

    private String resolveRouterValue(String key, Object[] args) {
        if (null == args || args.length == 0) {
            return null;
        }
        if (args.length == 1 && args[0] instanceof String) {
            return (String) args[0];
        }
        for (Object arg : args) {
            Object value = readFieldValue(arg, key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(args[0]);
    }

    private Object readFieldValue(Object target, String fieldName) {
        if (target == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

}
