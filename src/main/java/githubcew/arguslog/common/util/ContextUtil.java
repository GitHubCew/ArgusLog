package githubcew.arguslog.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 上下文工具
 *
 * @author chenenwei
 */
@Component
public class ContextUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    /**
     * 设置ApplicationContext
     *
     * @param applicationContext applicationContext
     * @throws BeansException 异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ContextUtil.applicationContext = applicationContext;
    }

    /**
     * 获取bean
     *
     * @param beanClass beanClass
     * @param <T>       泛型
     * @return bean
     */
    public static <T> T getBean(Class<T> beanClass) {
        return applicationContext.getBean(beanClass);
    }

}
