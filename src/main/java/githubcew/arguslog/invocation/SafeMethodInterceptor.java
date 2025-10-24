package githubcew.arguslog.invocation;

import org.aopalliance.intercept.MethodInvocation;

/**
 * @author chenenwei
 */
public abstract class SafeMethodInterceptor implements MethodLifecycleInterceptor {

    @Override
    public final Object invoke(MethodInvocation invocation) throws Throwable {
        Object result = null;
        Throwable exception = null;

        // 退出
        Object exit = exit(invocation);
        if (exit != null) {
            return exit;
        }

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

    /**
     * 退出方法调用
     * @param invocation 方法调用上下文
     * @return 退出方法调用，如果不为null，则返回该值作为方法调用结果
     */
     public Object exit(MethodInvocation invocation) {return null;};

    /**
     * 方法调用前执行
     * @param invocation 方法调用上下文
     */
    public abstract void beforeInvoke(MethodInvocation invocation);

    /**
     * 方法调用后执行
     * @param invocation 方法调用上下文
     * @param result 方法调用结果
     */
    public abstract void afterInvoke(MethodInvocation invocation, Object result);

    /**
     * 方法抛出异常执行
     * @param invocation 方法调用上下文
     * @param e          目标方法抛出的异常
     */
    public abstract void afterThrowing(MethodInvocation invocation, Throwable e);
}