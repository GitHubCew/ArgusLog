package githubcew.arguslog.monitor.trace.buddy;

import githubcew.arguslog.web.ArgusRequestContext;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

/**
 * 方法拦截器
 *
 * @author chenenwei
 */
public class MethodCallAdvice {

    /**
     * 进入方法
     */
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin Method method,
                               @Advice.AllArguments Object[] args) {
        ArgusRequestContext.startMethod(method, args);
    }

    /**
     * 退出方法
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Origin Method method,
                              @Advice.Return Object returnValue,
                              @Advice.Thrown Throwable throwable) {
            ArgusRequestContext.endMethod(method, returnValue, throwable);
    }
}