package githubcew.arguslog.core;

import githubcew.arguslog.monitor.trace.jdk.JdkProxyWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 可刷新代理 Bean 后置处理器
 *
 * @author chenenwei
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class ArgusBeanPostProcessor implements BeanPostProcessor {

    /**
     * 初始化后 处理bean
     * @param bean 原始bean
     * @param beanName bean名称
     * @return 处理后的bean
     * @throws BeansException 异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

        return JdkProxyWrapper.wrap(bean, beanName);
    }
}
