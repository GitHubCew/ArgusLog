package githubcew.arguslog.monitor.trace.jdk;

import org.springframework.beans.factory.DisposableBean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 可刷新的代理包装器
 *
 * @author chenenwei
 */
public class RefreshableProxy<T> implements InvocationHandler, DisposableBean {

    // 当前引用
    private volatile T currentTarget;
    // 原始引用
    private final T originalTarget;

    /**
     * 构造方法
     * @param initialTarget 初始目标对象
     */
    public RefreshableProxy(T initialTarget) {
        this.currentTarget = initialTarget;
        this.originalTarget = initialTarget;
    }

    /**
     * 方法调用
     * @param proxy 代理
     * @param method 方法
     * @param args 参数
     * @return 返回值
     * @throws Throwable 异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(currentTarget, args);
    }

    /**
     * 销毁
     * @throws Exception 异常
     */
    @Override
    public void destroy() throws Exception {
        // 如果需要清理 target
        this.currentTarget = null;
    }

    /**
     * 运行时刷新目标对象
     * @param newTarget 新的目标对象
     */
    public void refreshTarget(T newTarget) {
        this.currentTarget = newTarget;
    }

    /**
     * 恢复原始目标对象
     */
    public void restoreOriginal() {
        this.currentTarget = originalTarget;
    }

    /**
     * 获取当前目标对象
     * @return 对象
     */
    public T getCurrentTarget() {
        return currentTarget;
    }

    /**
     * 获取原始目标对象
     * @return 对象
     */
    public T getOriginalTarget() {
        return originalTarget;
    }

}