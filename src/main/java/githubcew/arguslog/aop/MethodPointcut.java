package githubcew.arguslog.aop;

import githubcew.arguslog.core.cache.ArgusCache;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 方法拦截
 * @author  chenenwei
 */
public class MethodPointcut implements Pointcut{

    /**
     * 获取类过滤器
     * @return 类过滤器
     */
    @Override
    public ClassFilter getClassFilter() {
        return clazz -> {
            Annotation[] annotations = clazz.getAnnotations();
            boolean flag = false;
            for (Annotation annotation : annotations) {
                flag =  "org.springframework.stereotype.Controller".equals(annotation.annotationType().getName())
                        ||  "org.springframework.web.bind.annotation.RestController".equals(annotation.annotationType().getName());
                if(flag)
                    break;
            }
            return flag;
        };
    }

    /**
     * 获取方法匹配器
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

                // 判断是否监听指定方法
                return ArgusCache.containsMethod(method);
            }
        };
    }
}
