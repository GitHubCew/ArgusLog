package githubcew.arguslog.aop;

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
            Annotation[] annotations = clazz.getAnnotations();
            for (Annotation annotation : annotations) {
                boolean api = "org.springframework.stereotype.Controller".equals(annotation.annotationType().getName())
                        || "org.springframework.web.bind.annotation.RestController".equals(annotation.annotationType().getName())
                        ;

                if (api) {
                    return true;
                }
            }

            // 检查类中是否有消费者方法
            for (Method method : clazz.getDeclaredMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    String annotationName = annotation.annotationType().getName();
                    if (annotationName.equals("org.springframework.amqp.rabbit.annotation.RabbitListener") ||
                            annotationName.contains("org.springframework.kafka.annotation.KafkaListener") ||
                            annotationName.contains("org.apache.rocketmq.spring.annotation.RocketMQMessageListener")) {
                        return true;
                    }
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
