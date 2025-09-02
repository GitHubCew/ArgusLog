package githubcew.arguslog.monitor.trace.buddy;

import githubcew.arguslog.monitor.trace.ArgusTraceRequestContext;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

/**
 * Argus trace 追踪拦截器
 *
 * @author chenenwei
 */
public class TracingAdvice {

    /**
     * 进入方法
     *
     * @param method 方法
     */
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin Method method) {
        ArgusTraceRequestContext.startMethod(method);
    }

    /**
     * 退出方法
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit() {
        ArgusTraceRequestContext.endMethod();
    }
}