package githubcew.arguslog.aop;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.invocation.ApiMethodInterceptor;
import githubcew.arguslog.invocation.MethodInterceptorChain;
import githubcew.arguslog.invocation.MqMethodInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * 方法增强
 *
 * @author chenenwei
 */
public class MethodAdvice implements MethodInterceptor {

    /**
     * 拦截方法
     *
     * @param invocation 方法调用
     * @return 返回值
     * @throws Throwable 异常
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        // 创建方法拦截器链
        MethodInterceptorChain methodInterceptorChain = new MethodInterceptorChain(invocation);

        // 添加api 方法拦截器
        boolean hasApiMethod = ArgusCache.containsMethod(invocation.getMethod());
        if (hasApiMethod) {
            methodInterceptorChain.addInterceptor(new ApiMethodInterceptor());
        }

        // 添加mq 方法拦截器
        boolean hasMqMethod = ArgusCache.getMqMonitorUser(invocation.getMethod()).size() > 0;
        if (hasMqMethod) {
            methodInterceptorChain.addInterceptor(new MqMethodInterceptor());
        }

        return methodInterceptorChain.invoke();
    }
}
