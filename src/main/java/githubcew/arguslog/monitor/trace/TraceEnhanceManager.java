package githubcew.arguslog.monitor.trace;

import githubcew.arguslog.common.util.ProxyUtil;
import githubcew.arguslog.monitor.trace.buddy.BuddyProxyManager;
import githubcew.arguslog.monitor.trace.buddy.TracingAdvice;
import githubcew.arguslog.monitor.trace.jdk.JdkProxyManager;
import githubcew.arguslog.monitor.trace.jdk.JdkProxyTracingAdvice;

import java.util.List;

/**
 * @author chenenwei
 *
 * trace 增强管理器
 */
public class TraceEnhanceManager {

    /**
     * 增强方法
     *
     * @param key key
     * @param targetClass 类型
     * @param methodNames  方法名
     */
    public static void enhanceMethods(String key, Class<?> targetClass, List<String> methodNames) {

        // jdk代理类
        if (ProxyUtil.isJdkProxyClass(targetClass)) {
            Class<?>[] proxyInterfaces = ProxyUtil.getProxyInterfaces(targetClass);
            for (Class<?> proxyInterface : proxyInterfaces) {
                try {
                    JdkProxyManager.proxyMethod(key, proxyInterface, JdkProxyTracingAdvice.class);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // 普通类 走buddy字节码增强
        else {
            BuddyProxyManager.enhanceMethods(key, targetClass, methodNames, TracingAdvice.class);
        }
    }

    /**
     * 根据key 移除增强
     * @param key 移除key
     */
    public static void revertClassWithKey(String key) {
        JdkProxyManager.revertProxyWithKey(key);
        BuddyProxyManager.revertClassWithKey(key);
    }


    /**
     * 移除指定类的增强
     * @param targetClass 类
     */
    public static void revertClass(Class<?> targetClass) {

        // jdk代理类
        if (ProxyUtil.isJdkProxyClass(targetClass)) {

            Class<?>[] proxyInterfaces = ProxyUtil.getProxyInterfaces(targetClass);
            for (Class<?> proxyInterface : proxyInterfaces) {
                JdkProxyManager.revertProxy(proxyInterface);
            }
        }

        // 普通类 走buddy 处理
        else {
            BuddyProxyManager.revertClass(targetClass);
        }
    }

    /**
     * 移除全部增强
     */
    public static void revertAllClasses() {
        JdkProxyManager.revertAllProxy();
        BuddyProxyManager.revertAllClasses();
    }
}
