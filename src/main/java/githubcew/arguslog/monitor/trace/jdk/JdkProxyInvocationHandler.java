package githubcew.arguslog.monitor.trace.jdk;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 自定义代理处理器
 *
 * @author chenenwei
 */
public class JdkProxyInvocationHandler implements InvocationHandler {

    // 目标对象
    protected Object target;

    public JdkProxyInvocationHandler () {

    }

    public Object getTarget() {
        return target;
    }

    public void setTarget(Object target) {
        this.target = target;
    }

    public JdkProxyInvocationHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        return null;
    }

}
