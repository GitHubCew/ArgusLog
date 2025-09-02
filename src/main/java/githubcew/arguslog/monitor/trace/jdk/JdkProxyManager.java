package githubcew.arguslog.monitor.trace.jdk;

import githubcew.arguslog.common.util.ContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author chenenwei
 *
 * Jdk代理对象管理器
 */
public class JdkProxyManager {

    private final static Logger log = LoggerFactory.getLogger(JdkProxyManager.class);

    // 已更新代理对象的类： key: 为类名 value: 代理对象
    private static final Map<String, Object> proxyClasses = new ConcurrentHashMap<>();

    // 方法关联更新的代理对象列表： key: 方法 value: 代理对象列表
    private static final Map<String, List<Object>> proxyClassesWithKey = new ConcurrentHashMap<>();


    /**
     * 代理方法
     * @param key key
     * @param proxyClass 代理类
     * @param handlerType 拦截器
     */
    public static <T> void proxyMethod(String key, Class<T> proxyClass, Class<? extends JdkProxyInvocationHandler> handlerType) {
        try {
            // 1. 获取 Spring 容器中的代理对象（它是一个 JDK 代理）
            T existingProxy = ContextUtil.getBean(proxyClass);

            // 2. 检查是否是 Proxy，且 handler 是 RefreshableProxy
            if (!Proxy.isProxyClass(existingProxy.getClass())) {
                return;
            }

            InvocationHandler currentHandler = Proxy.getInvocationHandler(existingProxy);
            if (!(currentHandler instanceof RefreshableProxy)) {
                return;
            }

            @SuppressWarnings("unchecked")
            RefreshableProxy<T> refreshableProxy = (RefreshableProxy<T>) currentHandler;

            // 获取原始目标
            T originalTarget = refreshableProxy.getOriginalTarget();

            // 创建新的 InvocationHandler
            JdkProxyInvocationHandler newHandler;
            try {
                newHandler = handlerType.newInstance();
                newHandler.setTarget(originalTarget); // 假设你有 setTarget 方法
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            // 创建新代理
            T newEnhancedProxy = (T) createProxy(originalTarget, newHandler);

            // 刷新 让 RefreshableProxy 指向这个新代理
            refreshableProxy.refreshTarget(newEnhancedProxy);

            proxyClasses.putIfAbsent(proxyClass.getName(), existingProxy);
            proxyClassesWithKey.computeIfAbsent(key, k -> new ArrayList<>()).add(existingProxy);

            if (log.isDebugEnabled()) {
                log.debug("Argus Enhanced object: {}" , existingProxy);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据key移除代理
     * @param key key
     */
    public static void revertProxyWithKey(String key) {
        if (proxyClassesWithKey.containsKey(key)) {
            for (Object proxy : proxyClassesWithKey.get(key)) {
                restoreOriginal(proxy);
            }
            proxyClassesWithKey.remove(key);
        }
    }

    /**
     * 根据类移除代理
     * @param targetClass  类
     */
    public static void revertProxy (Class<?> targetClass) {
        if (!proxyClasses.containsKey(targetClass.getName())) {
            return;
        }
        Object proxy = proxyClasses.get(targetClass.getName());
        restoreOriginal(proxy);
        proxyClasses.remove(targetClass.getName());
    }

    /**
     * 移除全部代理
     */
    public static void revertAllProxy () {
        for (Object proxy : proxyClasses.values()) {
            restoreOriginal(proxy);
        }
        proxyClasses.clear();
        proxyClassesWithKey.clear();
    }


    /**
     * 刷新带来对象
     * @param proxy 代理
     * @param newTarget 新的目标对象
     * @param <T> T
     */
    private static  <T> void refreshProxy(T proxy, T newTarget) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (handler instanceof RefreshableProxy) {
            ((RefreshableProxy<T>) handler).refreshTarget(newTarget);
        } else {
            throw new IllegalArgumentException("目标对象不是可刷新代理");
        }
    }

    /**
     * 恢复原始代理对象
     */
    private static  <T> void restoreOriginal(T proxy) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (handler instanceof RefreshableProxy) {
            ((RefreshableProxy<T>) handler).restoreOriginal();
        } else {
            throw new IllegalArgumentException("目标对象不是可刷新代理");
        }
    }

    /**
     * 创建代理
     *
     * @param target     目标对象
     * @param interfaces 接口
     * @return 代理对象
     */
    /**
     * 创建 JDK 动态代理（自动获取目标对象所有接口）
     */
    private static Object createProxy(Object target, InvocationHandler handler) {
        ClassLoader loader = target.getClass().getClassLoader();
        Class<?>[] interfaces = target.getClass().getInterfaces();

        if (interfaces.length == 0) {
            throw new IllegalArgumentException(
                    "Cannot create proxy for class " + target.getClass() +
                            ": it does not implement any interface"
            );
        }

        return Proxy.newProxyInstance(loader, interfaces, handler);
    }

}
