package githubcew.arguslog.invocation;

import org.aopalliance.intercept.MethodInvocation;

/**
 * 方法调用拦截器接口。
 * <p>
 * 该接口定义了方法执行生命周期中的关键扩展点，用于在方法调用前后、异常抛出时插入自定义逻辑（如日志记录、性能监控、
 * 安全校验、链路追踪等）。通常由 AOP 切面或代理机制调用，实现对目标方法的透明增强。
 * </p>
 * <p>
 * 实现类应保证线程安全，并避免在拦截逻辑中引入显著性能开销。
 * </p>
 *
 * @author chenenwei
 * @since 1.0.0
 */
public interface MethodLifecycleInterceptor {

    /**
     * 执行目标方法的完整调用链（包括前置、目标方法、后置、异常处理等）。
     * <p>
     * 此方法通常由代理或 AOP 框架调用，作为方法拦截的入口点。实现应协调 {@link #beforeInvoke}、
     * {@link #afterInvoke} 和 {@link #afterThrowing} 的调用顺序，并处理异常传播。
     * </p>
     *
     * @param invocation 封装了目标方法、参数、目标对象等信息的调用上下文
     * @return 目标方法的返回值
     * @throws Throwable 目标方法或拦截逻辑中抛出的任何异常
     */
    Object invoke(MethodInvocation invocation) throws Throwable;

    /**
     * 在目标方法执行前调用。
     * <p>
     * 适用于执行前置检查、参数校验、埋点开始、日志记录等操作。
     * </p>
     *
     * @param invocation 方法调用上下文
     */
    void beforeInvoke(MethodInvocation invocation);

    /**
     * 在目标方法成功执行后（未抛出异常）调用。
     * <p>
     * 适用于记录执行结果、性能指标上报、资源清理等操作。
     * </p>
     *
     * @param invocation 方法调用上下文
     */
    void afterInvoke(MethodInvocation invocation, Object object);

    /**
     * 在目标方法抛出异常时调用。
     * <p>
     * 适用于异常日志记录、告警通知、降级处理等操作。
     * 注意：此方法不应吞掉异常，除非明确需要屏蔽异常。
     * </p>
     *
     * @param invocation 方法调用上下文
     * @param e          目标方法抛出的异常
     */
    void afterThrowing(MethodInvocation invocation, Throwable e) throws Throwable;
}