package githubcew.arguslog.monitor.trace.jdk;

import githubcew.arguslog.web.ArgusRequestContext;

import java.lang.reflect.Method;

/**
 * Jdk代理对象增强器
 *
 * @author chenenwei
 */
public class JdkProxyMethodCallAdvice extends JdkProxyInvocationHandler {

    @Override
    public Object invoke (Object proxy, Method method, Object[] args) throws Throwable {

        try {
            // 方法开始
            ArgusRequestContext.startMethod(method, args);
        } catch (Exception e) {
            //
        }
        Object result = null;
        Throwable throwable = null;

        try {
            result =  method.invoke(target, args);
        }
        catch (Throwable e) {
            throwable = e;
        }
        finally {
            // 方法结束
            ArgusRequestContext.endMethod(method, result, throwable);
        }
        return result;
    }
}