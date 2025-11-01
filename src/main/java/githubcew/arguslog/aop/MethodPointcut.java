package githubcew.arguslog.aop;

import githubcew.arguslog.common.util.SpringUtil;
import githubcew.arguslog.core.cache.ArgusCache;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 方法拦截
 *
 * @author chenenwei
 */
public class MethodPointcut implements Pointcut {

    /**
     * 获取类过滤器
     *
     * @return 类过滤器
     */
    @Override
    public ClassFilter getClassFilter() {
        return clazz -> {

            // 判断是否是接口
            if (SpringUtil.hasAnnotation(clazz, SpringUtil.URI_ANNOTATION)) {
                return true;
            }

            // 判断方法是否有MQ注解
            for (Method method : clazz.getDeclaredMethods()) {
                if (SpringUtil.hasAnnotation(method, SpringUtil.MQ_ANNOTATION)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * 获取方法匹配器
     *
     * @return 方法匹配器
     */
    @Override
    public MethodMatcher getMethodMatcher() {
        return new MethodMatcher() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                // 静态检查 - 代理创建时执行
                // 这里返回true表示所有方法都可能在运行时被检查
                return true;
            }

            @Override
            public boolean isRuntime() {
                // 返回true表示每次方法调用都会检查
                return true;
            }

            @Override
            public boolean matches(Method method, Class<?> targetClass, Object... args) {

                // 判断是否监听指定api方法
                boolean hasApiMethod =  ArgusCache.containsMethod(method);

                // 判断是否监听指定mq方法
                boolean hasMqMethod = ArgusCache.getMqMonitorUser(method).size() > 0;

                return hasApiMethod || hasMqMethod;
            }
        };
    }
}
