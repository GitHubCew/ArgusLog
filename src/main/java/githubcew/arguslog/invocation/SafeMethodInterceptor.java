package githubcew.arguslog.invocation;

import org.aopalliance.intercept.MethodInvocation;

/**
 * @author Envi
 */
public abstract class SafeMethodInterceptor implements MethodLifecycleInterceptor {

    @Override
    public final Object invoke(MethodInvocation invocation) throws Throwable {
        Object result = null;
        Throwable exception = null;

        // 安全执行前置处理
        safeBeforeInvoke(invocation);

        try {
            // 执行原方法
            result = invocation.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            // 安全执行异常处理
            safeAfterThrowing(invocation, e);
            throw e;
        } finally {
            // 安全执行后置处理
            safeAfterInvoke(invocation, result, exception);
        }
    }

    /**
     * 安全的前置处理 - 不会影响主流程
     */
    private void safeBeforeInvoke(MethodInvocation invocation) {
        try {
            beforeInvoke(invocation);
        } catch (Exception e) {
            // 记录日志但不影响主流程
            logInterceptorError("beforeInvoke", invocation, e);
        }
    }

    /**
     * 安全的异常处理 - 不会影响主流程
     */
    private void safeAfterThrowing(MethodInvocation invocation, Throwable e) {
        try {
            afterThrowing(invocation, e);
        } catch (Exception ex) {
            // 记录日志但不影响主流程
            logInterceptorError("afterThrowing", invocation, ex);
        }
    }

    /**
     * 安全的后置处理 - 不会影响主流程
     */
    private void safeAfterInvoke(MethodInvocation invocation, Object result, Throwable exception) {
        try {
            if (exception == null) {
                afterInvoke(invocation, result);
            }
        } catch (Exception e) {
            // 记录日志但不影响主流程
            logInterceptorError("afterInvoke", invocation, e);
        }
    }

    private void logInterceptorError(String phase, MethodInvocation invocation, Exception e) {
        // 这里可以记录到日志系统
        System.err.println("拦截器 " + getClass().getSimpleName() + " 在 " + phase +
                " 阶段执行失败，方法: " + invocation.getMethod().getName());
        e.printStackTrace();
    }

    // 抽象方法 - 子类只需实现这些，无需关心异常处理

    public abstract void beforeInvoke(MethodInvocation invocation);
    public abstract void afterInvoke(MethodInvocation invocation, Object result);
    public abstract void afterThrowing(MethodInvocation invocation, Throwable e);
}