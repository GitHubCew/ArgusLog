package githubcew.arguslog.common.util;

import org.springframework.aop.support.AopUtils;
import org.springframework.cglib.proxy.Factory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * 代理工具类，用于判断对象或类是否为各种类型的代理（如 JDK 动态代理、CGLIB 代理、Spring AOP 代理、MyBatis Mapper 代理、Mockito Mock 等），
 * 并提供从代理类名中提取原始类名的功能。
 * <p>
 * 本工具类适用于运行时诊断、日志记录、反射处理等需要识别代理对象真实类型的场景。
 *
 * @author chenenwei
 */
public class ProxyUtil {

    /**
     * 判断指定的 Class 是否为 JDK 动态代理类。
     * <p>
     * JDK 动态代理类由 {@link Proxy} 生成，通常类名以 {@code $Proxy} 开头。
     *
     * @param clazz 待检测的类对象
     * @return 如果是 JDK 动态代理类则返回 {@code true}，否则返回 {@code false}
     * @see Proxy#isProxyClass(Class)
     */
    public static boolean isJdkProxyClass(Class<?> clazz) {
        return java.lang.reflect.Proxy.isProxyClass(clazz);
    }

    /**
     * 判断指定的对象是否为 JDK 动态代理实例。
     * <p>
     * 若对象为 {@code null}，则返回 {@code false}。
     *
     * @param object 待检测的对象
     * @return 如果是 JDK 动态代理实例则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isJdkProxyClass(Object object) {
        if (object == null) {
            return false;
        }
        return isJdkProxyClass(object.getClass());
    }

    /**
     * 判断指定的 Class 是否为 CGLIB 代理类（通用判断）。
     * <p>
     * 判断依据包括：
     * <ul>
     *   <li>类名包含 {@code $$EnhancerBy}、{@code ByCGLIB} 或 {@code $$FastClassBy}</li>
     *   <li>实现 {@link Factory} 接口（CGLIB 代理的标记接口）</li>
     * </ul>
     *
     * @param clazz 待检测的类对象
     * @return 如果是 CGLIB 代理类则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isCglibProxy(Class<?> clazz) {
        String name = clazz.getName();
        return name.contains("$$EnhancerBy") ||
                name.contains("ByCGLIB") ||
                name.contains("$$FastClassBy") ||
                Factory.class.isAssignableFrom(clazz);
    }

    /**
     * 判断指定的对象是否为 CGLIB 代理实例（通用判断）。
     * <p>
     * 若对象为 {@code null}，则返回 {@code false}。
     *
     * @param object 待检测的对象
     * @return 如果是 CGLIB 代理实例则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isCglibProxy(Object object) {
        if (object == null) {
            return false;
        }
        String name = object.getClass().getName();
        return name.contains("$$EnhancerBy") ||
                name.contains("ByCGLIB") ||
                name.contains("$$FastClassBy") ||
                Factory.class.isAssignableFrom(object.getClass());
    }

    /**
     * 判断指定的 Class 是否为 Spring {@code @Configuration} 类的 CGLIB 代理。
     * <p>
     * Spring 为 {@code @Configuration} 类生成的 CGLIB 代理类名通常包含 {@code $$EnhancerBySpringCGLIB$$}，
     * 且其父类为 {@code ConfigurationClassEnhancer$...}。
     *
     * @param targetClass 待检测的类对象
     * @return 如果是 Spring Configuration CGLIB 代理则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isSpringConfigurationCglibProxy(Class<?> targetClass) {
        if (!targetClass.getName().contains("$$EnhancerBySpringCGLIB$$")) {
            return false;
        }
        Class<?> superclass = targetClass.getSuperclass();
        return superclass != null &&
                superclass.getName().startsWith("org.springframework.context.annotation.ConfigurationClassEnhancer$");
    }

    /**
     * 判断指定的对象是否为 Spring {@code @Configuration} 类的 CGLIB 代理实例。
     * <p>
     * 若对象为 {@code null}，则返回 {@code false}。
     *
     * @param bean 待检测的对象
     * @return 如果是 Spring Configuration CGLIB 代理实例则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isSpringConfigurationCglibProxy(Object bean) {
        if (bean == null) {
            return false;
        }
        Class<?> targetClass = bean.getClass();
        if (!targetClass.getName().contains("$$EnhancerBySpringCGLIB$$")) {
            return false;
        }
        Class<?> superclass = targetClass.getSuperclass();
        return superclass != null &&
                superclass.getName().startsWith("org.springframework.context.annotation.ConfigurationClassEnhancer$");
    }

    /**
     * 判断指定的对象是否为 Spring AOP 代理（如事务、缓存、自定义切面等）。
     * <p>
     * 底层调用 {@link AopUtils#isAopProxy(Object)}。
     *
     * @param obj 待检测的对象
     * @return 如果是 Spring AOP 代理则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isSpringAopProxy(Object obj) {
        return AopUtils.isAopProxy(obj);
    }

    /**
     * 判断指定的对象是否为 MyBatis Mapper 代理实例。
     * <p>
     * MyBatis 使用 JDK 动态代理为 Mapper 接口生成代理，其 {@link InvocationHandler} 为 {@code MapperProxy}。
     *
     * @param obj 待检测的对象
     * @return 如果是 MyBatis Mapper 代理则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isMyBatisMapperProxy(Object obj) {
        if (obj == null || !Proxy.isProxyClass(obj.getClass())) {
            return false;
        }
        InvocationHandler handler = Proxy.getInvocationHandler(obj);
        return handler != null && handler.getClass().getName().contains("MapperProxy");
    }

    /**
     * 判断指定的对象是否为 Mockito Mock 实例。
     * <p>
     * Mockito Mock 类名通常包含 {@code Mockito} 或 {@code EnhancerByMockito}。
     *
     * @param obj 待检测的对象
     * @return 如果是 Mockito Mock 实例则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isMockitoMock(Object obj) {
        if (obj == null) return false;
        Class<?> clazz = obj.getClass();
        return clazz.getName().contains("Mockito") ||
                clazz.getName().contains("EnhancerByMockito");
    }

    /**
     * 获取 JDK 动态代理类所实现的所有接口。
     * <p>
     * 若传入的类不是 JDK 代理类，则返回空数组。
     *
     * @param proxyClass 代理类
     * @return 代理类实现的接口数组；若非 JDK 代理类，返回长度为 0 的数组
     */
    public static Class<?>[] getProxyInterfaces(Class<?> proxyClass) {
        if (!isJdkProxyClass(proxyClass)) {
            return new Class[0];
        }
        return proxyClass.getInterfaces();
    }

    /**
     * 从类全限定名中提取原始类名（去除代理后缀）。
     * <p>
     * 支持的代理格式包括：
     * <ul>
     *   <li>Spring CGLIB: {@code xxx$$EnhancerBySpringCGLIB$$...}</li>
     *   <li>原生 CGLIB: {@code xxx$$EnhancerByCGLIB$$...} 或 {@code xxx$$ByCGLIB$$...}</li>
     *   <li>Hibernate: {@code xxx_$$$_jvst_...}</li>
     *   <li>Mockito: {@code xxx$MockitoMock$...} 或 {@code xxx$$EnhancerByMockito$$...}</li>
     *   <li>JDK 代理: {@code com.sun.proxy.$Proxy...} 或 {@code $Proxy...} —— 保留原名（无原始实现类）</li>
     * </ul>
     * <p>
     * 若无法识别或非代理类，返回原类名。
     *
     * @param className 代理类或普通类的全限定名
     * @return 提取后的原始类全限定名；若无法提取或不是代理类，返回原类名
     */
    public static String extractOriginalClassName(String className) {
        if (className == null) {
            return null;
        }

        // 1. Spring CGLIB 代理
        int springCglibIndex = className.indexOf("$$EnhancerBySpringCGLIB$$");
        if (springCglibIndex != -1) {
            return className.substring(0, springCglibIndex);
        }

        // 2. 原生 CGLIB 代理 (EnhancerByCGLIB)
        int cglibIndex = className.indexOf("$$EnhancerByCGLIB$$");
        if (cglibIndex != -1) {
            return className.substring(0, cglibIndex);
        }

        // 3. 原生 CGLIB 代理 (ByCGLIB)
        int byCglibIndex = className.indexOf("$$ByCGLIB$$");
        if (byCglibIndex != -1) {
            return className.substring(0, byCglibIndex);
        }

        // 4. Hibernate 代理 (常见格式: _$$_jvst_...)
        int hibernateIndex = className.indexOf("_$$$_jvst_");
        if (hibernateIndex != -1) {
            return className.substring(0, hibernateIndex);
        }

        // 5. Mockito Mock (xxx$MockitoMock$...)
        int mockitoIndex = className.indexOf("$MockitoMock$");
        if (mockitoIndex != -1) {
            return className.substring(0, mockitoIndex);
        }

        // 6. 其他 Mockito 格式 (EnhancerByMockito)
        int mockitoEnhancerIndex = className.indexOf("$$EnhancerByMockito$$");
        if (mockitoEnhancerIndex != -1) {
            return className.substring(0, mockitoEnhancerIndex);
        }

        // 7. JDK 动态代理 (com.sun.proxy.$Proxy123 或 $Proxy123)
        // 注意：JDK 代理没有“原始实现类”，只有接口
        // 这里不提取，因为无法确定是哪个接口（可能实现多个）
        // 保持原样，由调用方决定如何处理
        if (className.startsWith("com.sun.proxy.$Proxy") ||
                className.startsWith("$Proxy") && Character.isDigit(className.charAt(6))) {
            // 返回原类名，因为 JDK 代理没有单一“原始类”
            return className;
        }

        // 8. MyBatis Mapper Proxy (类名不变，但可以识别)
        // MyBatis 不改变类名，代理类名就是接口名
        // 所以直接返回

        // 9. 其他未知格式 → 返回原类名
        return className;
    }

    /**
     * 判断给定的类名是否为已知代理类名。
     * <p>
     * 支持识别 Spring CGLIB、原生 CGLIB、Hibernate、Mockito、JDK 代理等格式。
     *
     * @param className 类全限定名
     * @return 如果是已知代理类名则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isProxyClassName(String className) {
        if (className == null) return false;

        return className.contains("$$EnhancerBySpringCGLIB$$") ||
                className.contains("$$EnhancerByCGLIB$$") ||
                className.contains("$$ByCGLIB$$") ||
                className.contains("_$$$_jvst_") ||
                className.contains("$MockitoMock$") ||
                className.contains("$$EnhancerByMockito$$") ||
                (className.startsWith("com.sun.proxy.$Proxy") && className.contains("$Proxy")) ||
                (className.startsWith("$Proxy") && className.length() > 6 && Character.isDigit(className.charAt(6)));
    }

    /**
     * 从 {@link Class} 对象中提取原始类全限定名（去除代理后缀）。
     * <p>
     * 底层调用 {@link #extractOriginalClassName(String)}。
     *
     * @param clazz 类对象
     * @return 提取后的原始类全限定名；若无法提取或不是代理类，返回原类名；若传入 {@code null}，返回 {@code null}
     */
    public static String extractOriginalClassName(Class<?> clazz) {
        if (clazz == null) return null;
        return extractOriginalClassName(clazz.getName());
    }

    /**
     * 从对象实例中提取其原始类全限定名（去除代理后缀）。
     * <p>
     * 底层调用 {@link #extractOriginalClassName(String)}。
     *
     * @param obj 对象实例
     * @return 提取后的原始类全限定名；若无法提取或不是代理类，返回原类名；若传入 {@code null}，返回 {@code null}
     */
    public static String extractOriginalClassNameFromObject(Object obj) {
        if (obj == null) return null;
        return extractOriginalClassName(obj.getClass().getName());
    }
}