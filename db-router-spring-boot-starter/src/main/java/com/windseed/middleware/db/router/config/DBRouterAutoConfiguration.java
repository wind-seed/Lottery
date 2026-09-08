package com.windseed.middleware.db.router.config;

import com.windseed.middleware.db.router.strategy.IDBRouterStrategy;
import com.windseed.middleware.db.router.strategy.impl.DBRouterStrategyHashCode;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
public class DBRouterAutoConfiguration {

    private static final String PREFIX = "mini-db-router.jdbc.datasource";

    @Value("${mini-db-router.jdbc.datasource.dbCount:1}")
    private int dbCount;

    @Value("${mini-db-router.jdbc.datasource.tbCount:1}")
    private int tbCount;

    @Value("${mini-db-router.jdbc.datasource.default:db00}")
    private String defaultDbKey;

    @Value("${mini-db-router.jdbc.datasource.routerKey:uId}")
    private String routerKey;

    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource(Environment env) {
        String list = env.getProperty(PREFIX + ".list", "");
        Map<Object, Object> targetDataSources = new HashMap<>();

        DataSource defaultDataSource = buildDataSource(env, defaultDbKey);
        targetDataSources.put(defaultDbKey, defaultDataSource);
        for (String dbKey : list.split(",")) {
            String trimmed = dbKey.trim();
            if (!trimmed.isEmpty()) {
                targetDataSources.put(trimmed, buildDataSource(env, trimmed));
            }
        }

        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        dynamicDataSource.setDefaultTargetDataSource(defaultDataSource);
        dynamicDataSource.setTargetDataSources(targetDataSources);
        return dynamicDataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public IDBRouterStrategy dbRouterStrategy() {
        return new DBRouterStrategyHashCode(dbCount, tbCount, defaultDbKey);
    }

    @Bean
    public DBRouterJoinPoint dbRouterJoinPoint(IDBRouterStrategy dbRouterStrategy) {
        return new DBRouterJoinPoint(dbRouterStrategy, routerKey);
    }

    @Bean
    public Interceptor dynamicMybatisTableInterceptor() {
        return new DynamicMybatisTableInterceptor();
    }

    private DataSource buildDataSource(Environment env, String dbKey) {
        String base = PREFIX + "." + dbKey + ".";
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(env.getRequiredProperty(base + "driver-class-name"));
        dataSource.setUrl(env.getRequiredProperty(base + "url"));
        dataSource.setUsername(env.getRequiredProperty(base + "username"));
        dataSource.setPassword(env.getProperty(base + "password", ""));
        return dataSource;
    }

}
