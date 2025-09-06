package githubcew.arguslog.monitor.trace.jdk;

/**
 * JDK代理工具类
 *
 * @author chenenwei
 */
public class JdkProxyUtils {

    /**
     * 判断是否是JDK动态代理类
     * @param clazz 类
     * @return 是否是JDK动态代理类
     */
    public static boolean isJdkProxyClass(Class<?> clazz) {
        return java.lang.reflect.Proxy.isProxyClass(clazz);
    }

    /**
     * 获取JDK代理的目标接口
     * @param proxyClass 代理类
     * @return 代理类所实现的接口
     */
    public static Class<?>[] getProxyInterfaces(Class<?> proxyClass) {
        if (!isJdkProxyClass(proxyClass)) {
            return new Class[0];
        }
        return proxyClass.getInterfaces();
    }

}