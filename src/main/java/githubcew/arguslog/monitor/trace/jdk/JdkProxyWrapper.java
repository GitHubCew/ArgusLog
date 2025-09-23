package githubcew.arguslog.monitor.trace.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * jdk 动态代理包装器
 *
 * @author chenenwei
 */
public class JdkProxyWrapper {

    private final static Logger log = LoggerFactory.getLogger(JdkProxyWrapper.class);

    /**
     * 包装对象
     * @param bean bean对象
     * @param beanName bean名称
     * @return 包装后的对象
     */
    public static Object wrap (Object bean, String beanName) {

        // 排除基础设施 Bean 和特定排除的 Bean
        if (Excluded.shouldExclude(bean)) {
            return bean;
        }

        // 只处理 JDK 动态代理
        if (!Proxy.isProxyClass(bean.getClass())) {
            return bean;
        }

        // 获取当前 InvocationHandler
        InvocationHandler handler = Proxy.getInvocationHandler(bean);

        // 避免重复包装
        if (handler instanceof RefreshableProxy) {
            return bean;
        }

        // 排除 Spring AOP 代理
        if (handler instanceof Advised) {
            return bean;
        }

        // 创建 RefreshableProxy
        try {
            RefreshableProxy<Object> refreshableProxy = new RefreshableProxy<>(bean);

            Object wrappedProxy = Proxy.newProxyInstance(
                    bean.getClass().getClassLoader(),
                    bean.getClass().getInterfaces(),
                    refreshableProxy
            );

            if (log.isDebugEnabled()) {
                log.info("【Argus => Successfully wrapped bean: {} 】", beanName);
            }
            return wrappedProxy;

        } catch (Exception e) {
            log.error("【Argus => Failed to wrap bean {}: {} 】", beanName, e.getMessage());
            return bean;
        }
    }


    /**
     * 排除配置
     */
    public static class Excluded {

        // 排除类
        private static final Set<String> EXCLUDE_CLASSES = new HashSet<>(Arrays.asList(
                // 数据源相关
                "javax.sql.DataSource",
                "com.zaxxer.hikari.HikariDataSource",
                "com.zaxxer.hikari.HikariConfig",
                "org.apache.tomcat.jdbc.pool.DataSource",
                "com.alibaba.druid.pool.DruidDataSource",
                "org.springframework.jdbc.datasource.DriverManagerDataSource",

                // JDBC 相关
                "org.springframework.jdbc.core.JdbcTemplate",
                "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
                "org.springframework.jdbc.datasource.DataSourceTransactionManager",

                // 事务相关
                "org.springframework.transaction.PlatformTransactionManager",
                "org.springframework.orm.jpa.JpaTransactionManager",

                // 调度相关
                "org.springframework.scheduling.TaskScheduler",
                "org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler",

                // 缓存相关
                "org.springframework.cache.CacheManager",

                // JPA 相关
                "org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean",
                "org.springframework.orm.jpa.AbstractEntityManagerFactoryBean",

                // MyBatis 相关（如果需要排除）
                "org.mybatis.spring.SqlSessionFactoryBean",
                "org.mybatis.spring.SqlSessionTemplate",

                // HikariCP 特定类
                "com.zaxxer.hikari.pool.HikariPool",
                "com.zaxxer.hikari.metrics.MetricsTrackerFactory"
        ));

        // 排除包
        private static final Set<String> EXCLUDE_PACKAGES = new HashSet<>(Arrays.asList(
                "com.zaxxer.hikari",
                "org.springframework.jdbc",
                "org.springframework.transaction",
                "org.springframework.orm",
                "org.springframework.scheduling",
                "com.alibaba.druid.pool"
        ));

        /**
         * 排除特定类
         *
         * @param clazz 类
         * @return 结果
         */
        private static boolean shouldExclude(Class<?> clazz) {
            if (clazz == null || clazz == Object.class) {
                return false;
            }

            // 检查当前类的名称
            String currentClassName = clazz.getName();
            if (EXCLUDE_CLASSES.contains(currentClassName)) {
                return true;
            }

            // 检查包名
            Package pkg = clazz.getPackage();
            if (pkg != null) {
                String packageName = pkg.getName();
                for (String criticalPackage : EXCLUDE_PACKAGES) {
                    if (packageName.startsWith(criticalPackage)) {
                        return true;
                    }
                }
            }

            // 递归检查父类
            if (shouldExclude(clazz.getSuperclass())) {
                return true;
            }

            // 递归检查所有接口
            for (Class<?> interfaceClass : clazz.getInterfaces()) {
                if (shouldExclude(interfaceClass)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * 校验是否是关键基础设施类
         *
         * @param bean 对象
         * @return 是否是关键基础设施类
         */
        public static boolean shouldExclude(Object bean) {
            if (bean == null) {
                return false;
            }
            try {
                return shouldExclude(bean.getClass());
            } catch (Exception e) {
                // 记录错误但继续执行
                return false;
            }
        }

        /**
         * 新增排除类
         *
         * @param className 类名
         */
        public static void addExcludeClass(String className) {
            EXCLUDE_CLASSES.add(className);
        }

        /**
         * 新增排除包
         *
         * @param packageName 包名
         */
        public static void addExcludePackage(String packageName) {
            EXCLUDE_PACKAGES.add(packageName);
        }
    }
}
