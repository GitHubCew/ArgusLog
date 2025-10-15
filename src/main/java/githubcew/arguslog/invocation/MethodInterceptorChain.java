package githubcew.arguslog.invocation;

import org.aopalliance.intercept.MethodInvocation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 方法拦截器链 - 负责管理多个拦截器的执行顺序
 */
public class MethodInterceptorChain {
    private final List<MethodLifecycleInterceptor> interceptors = new ArrayList<>();
    private final MethodInvocation targetInvocation;

    public MethodInterceptorChain(MethodInvocation targetInvocation) {
        this.targetInvocation = targetInvocation;
    }

    public void addInterceptor(MethodLifecycleInterceptor interceptor) {
        if (!interceptors.contains(interceptor)) {
            interceptors.add(interceptor);
        }
    }

    public Object invoke() throws Throwable {
        return new ChainedInvocation(interceptors, targetInvocation).proceed();
    }

    /**
     * 链式调用核心实现
     */
    private static class ChainedInvocation {
        private final List<MethodLifecycleInterceptor> interceptors;
        private final MethodInvocation targetInvocation;
        private int currentIndex = 0;

        public ChainedInvocation(List<MethodLifecycleInterceptor> interceptors,
                                 MethodInvocation targetInvocation) {
            this.interceptors = interceptors;
            this.targetInvocation = targetInvocation;
        }

        public Object proceed() throws Throwable {
            // 如果还有拦截器，执行下一个拦截器
            if (currentIndex < interceptors.size()) {
                MethodLifecycleInterceptor next = interceptors.get(currentIndex);

                // 创建下一个链的调用（currentIndex + 1）
                ChainedInvocation nextChain = new ChainedInvocation(
                        interceptors, targetInvocation);
                nextChain.currentIndex = this.currentIndex + 1;

                // 执行当前拦截器，并传入能够调用下一个链的代理
                return next.invoke(createProxyInvocation(nextChain));
            } else {
                // 执行最终目标方法
                return targetInvocation.proceed();
            }
        }

        /**
         * 创建代理调用，让拦截器能够继续链式调用
         */
        private MethodInvocation createProxyInvocation(ChainedInvocation nextChain) {
            return new MethodInvocation() {
                @Override
                public Object proceed() throws Throwable {
                    // 明确调用下一个链的proceed方法
                    return nextChain.proceed();
                }

                @Override
                public Method getMethod() {
                    return targetInvocation.getMethod();
                }

                @Override
                public Object[] getArguments() {
                    return targetInvocation.getArguments();
                }

                @Override
                public Object getThis() {
                    return targetInvocation.getThis();
                }

                @Override
                public AccessibleObject getStaticPart() {
                    return targetInvocation.getStaticPart();
                }
            };
        }
    }
}