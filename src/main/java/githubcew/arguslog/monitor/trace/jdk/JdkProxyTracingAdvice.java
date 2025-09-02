package githubcew.arguslog.monitor.trace.jdk;

import githubcew.arguslog.monitor.trace.ArgusTraceRequestContext;

import java.lang.reflect.Method;

/**
 * Jdk代理对象增强器
 *
 * @author chenenwei
 */
public class JdkProxyTracingAdvice extends JdkProxyInvocationHandler {

    @Override
    public Object invoke (Object proxy, Method method, Object[] args) throws Throwable {

        try {
            // 方法开始
            ArgusTraceRequestContext.startMethod(method);
        } catch (Exception e) {
            //
        }

        try {
            return method.invoke(target, args);

        } finally {
            // 方法结束
            ArgusTraceRequestContext.endMethod();
        }
    }
}