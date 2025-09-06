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

    /**
     * 构造方法
     */
    public JdkProxyInvocationHandler () {

    }

    /**
     * 获取目标对象
     * @return 目标对象
     */
    public Object getTarget() {
        return target;
    }

    /**
     * 设置目标对象
     * @param target 对象
     */
    public void setTarget(Object target) {
        this.target = target;
    }

    /**
     * 设置目标对象
     * @param target 目标对象
     */
    public JdkProxyInvocationHandler(Object target) {
        this.target = target;
    }

    /**
     * 方法调用
     * @param proxy 代理
     * @param method 方法
     * @param args 参数
     * @return 结果
     * @throws Throwable 异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        return null;
    }

}
