package githubcew.arguslog.monitor.trace.jdk;

import githubcew.arguslog.common.util.ContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * @author Envi
 */
public class JdkProxyWrapper {

    private final static Logger log = LoggerFactory.getLogger(JdkProxyWrapper.class);

    /**
     * 包装Proxy
     */
    public static void wrapJdkProxies() {
        ApplicationContext applicationContext = ContextUtil.context();
        if (Objects.isNull(applicationContext)) {
            return;
        }
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) applicationContext;
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ctx.getBeanFactory();

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            try {
                // 注意：prototype bean 可能会多次创建，谨慎处理
                if (beanFactory.isSingleton(beanName)) {
                    Object bean = beanFactory.getBean(beanName);
                    if (isEligibleForWrapping(bean)) {
                        wrapProxyIfNecessary(beanName, bean, beanFactory);
                        if (log.isDebugEnabled()) {
                            log.debug("Argus => Warped beanName:  {}", beanName);
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略无法获取的 Bean（如需要参数的 prototype）
            }
        }
    }

    /**
     *  判断是否是需要包装
     * @param bean bean
     * @return 结果
     */
    private static boolean isEligibleForWrapping(Object bean) {
        // 必须是 JDK 动态代理
        if (!Proxy.isProxyClass(bean.getClass())) {
            if (log.isDebugEnabled()) {
                log.debug("Argus => Skipping non-JDK proxy: {}", bean);
            }
            return false;
        }

        // 获取当前 InvocationHandler
        InvocationHandler handler = Proxy.getInvocationHandler(bean);

        // 避免重复包装：如果已经是 RefreshableProxy，跳过
        if (handler instanceof RefreshableProxy) {
            return false;
        }

        // 可选：排除 Spring AOP 的代理（如果你不想增强 AOP）
        // 如果你希望连 AOP 一起包装，就去掉这个判断
        // Spring AOP 代理（如 @Transactional），建议单独处理
        if (handler instanceof Advised) {
            if (log.isDebugEnabled()) {
                log.debug("Argus => Skipping Spring AOP proxy: {}", bean);
            }
            return false;
        }

        // 可选：排除其他已知的特殊代理
        String handlerClassName = handler.getClass().getName();
        return !handlerClassName.contains("Lambda") &&
                !handlerClassName.contains("CGlib");
    }

    /**
     * 包装代理
     * @param beanName bean名称
     * @param originalProxy 原代理
     * @param beanFactory bean工厂
     */
    private static void wrapProxyIfNecessary(String beanName, Object originalProxy, DefaultListableBeanFactory beanFactory) {
        // 再次确认类型
        if (!Proxy.isProxyClass(originalProxy.getClass())) {
            return;
        }

        // 获取原始 InvocationHandler（即被代理的逻辑）
        InvocationHandler originalHandler = Proxy.getInvocationHandler(originalProxy);

        // 创建可刷新的代理，目标是原始的 handler 所代理的对象逻辑
        RefreshableProxy<Object> refreshableProxy = new RefreshableProxy<>(originalProxy);

        // 重新生成代理对象，使用 refreshableProxy 作为新的 handler
        Object enhancedProxy = Proxy.newProxyInstance(
                originalProxy.getClass().getClassLoader(),
                originalProxy.getClass().getInterfaces(),
                refreshableProxy
        );

        // 替换容器中的单例
        if (beanFactory.containsSingleton(beanName)) {
            beanFactory.destroySingleton(beanName);
        }
        beanFactory.registerSingleton(beanName, enhancedProxy);
    }
}
