package githubcew.arguslog.monitor.sql;

import githubcew.arguslog.common.util.ProxyUtil;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.monitor.ArgusMethod;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * DAO 方法检测器，用于从当前线程调用栈中识别出数据访问层（DAO/Repository/Mapper）或服务层（Service）的方法。
 * <p>
 * 该类主要用于日志追踪、性能监控等场景，通过分析调用栈自动识别业务方法，优先识别 Mapper/Repository 接口方法，
 * 若未找到则降级到 Service 层方法。
 * </p>
 * <p>
 * 支持动态识别 JDK 动态代理和 CGLIB 代理背后的真实接口，并提供配置接口以扩展包名、类名后缀、方法前缀等规则。
 * </p>
 *
 * <h2>线程安全性</h2>
 * <p>本类是线程安全的。所有共享状态均使用线程安全的集合（如 {@link CopyOnWriteArraySet} 和 {@link ConcurrentHashMap}）维护。</p>
 *
 * @author chenenwei
 */
public class DaoMethodDetector {

    // ==================== 配置项 ====================

    /**
     * 显式包含的包名前缀集合（以点结尾），用于覆盖默认排除规则。
     * <p>若类名以该集合中任意字符串开头，则不会被排除。</p>
     */
    private static final Set<String> INCLUDE_PACKAGES = new CopyOnWriteArraySet<>();

    /**
     * 显式排除的包名前缀集合（以点结尾）。
     * <p>若类名以该集合中任意字符串开头，则会被跳过，不参与方法识别。</p>
     */
    private static final Set<String> EXCLUDE_PACKAGES = new CopyOnWriteArraySet<>();

    /**
     * 被视为 DAO/Repository 类的类名后缀集合（如 "Mapper", "Dao"）。
     * <p>用于判断一个类是否属于数据访问层。</p>
     */
    private static final Set<String> INCLUDE_CLASS_SUFFIXES = new CopyOnWriteArraySet<>();

    /**
     * 被视为业务方法的方法名前缀集合（如 "get", "save", "delete"）。
     * <p>用于在 Service 层识别有效业务方法。</p>
     */
    private static final Set<String> INCLUDE_METHOD_PREFIXES = new CopyOnWriteArraySet<>();

    /**
     * 常见的 Mapper/Repository 接口类名后缀。
     */
    private static final Set<String> MAPPER_SUFFIXES = new CopyOnWriteArraySet<>(Arrays.asList(
            "Mapper", "Repository", "Dao", "DAO"
    ));

    /**
     * 代理类到其真实接口的映射缓存，用于避免重复解析代理类。
     * <p>键：代理类全限定名；值：真实接口全限定名。</p>
     */
    private static final Map<String, String> PROXY_TO_INTERFACE_CACHE = new ConcurrentHashMap<>();

    static {
        // 默认排除常见框架内部类
        EXCLUDE_PACKAGES.addAll(Arrays.asList(
                "java.", "sun.", "org.springframework.",
                "org.apache.ibatis.", "com.baomidou.mybatisplus.",
                "org.hibernate.", "javax.persistence.", "com.zaxxer.hikari.",
                "org.mybatis"
        ));

        // 默认包含常见的业务方法前缀
        INCLUDE_METHOD_PREFIXES.addAll(Arrays.asList(
                "get", "find", "select", "query", "search", "list", "load",
                "save", "insert", "add", "create", "update", "modify",
                "delete", "remove", "count", "check", "exists"
        ));

        // 默认将常见后缀视为 DAO/Repository 类
        INCLUDE_CLASS_SUFFIXES.addAll(MAPPER_SUFFIXES);
    }

    /**
     * 从当前线程调用栈中检测 DAO 或 Service 层方法。
     * <div>
     * 检测策略：
     * <ol>
     *   <li>优先查找 Mapper/Repository 接口方法（包括通过代理调用的情况）；</li>
     *   <li>若未找到，则降级查找 Service 层方法。</li>
     * </ol>
     * </div>
     * @return 检测到的方法信息，若未识别则返回 {@link MethodInfo#UNKNOWN}
     */
    public static MethodInfo detect() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        // 策略1：优先查找Mapper/Repository接口方法
        MethodInfo mapperMethod = findMapperOrRepositoryMethod(stackTrace);
        if (!mapperMethod.isUnknown()) {
            return mapperMethod;
        }

        // 策略2：查找Service层方法（降级方案）
        MethodInfo serviceMethod = findServiceMethod(stackTrace);
        if (!serviceMethod.isUnknown()) {
            return serviceMethod;
        }

        return MethodInfo.UNKNOWN;
    }

    /**
     * 从调用栈中检测 Web 请求入口方法（如 Controller 方法）。
     * <p>
     * 该方法依赖 {@link ArgusCache} 中缓存的已知 {@link ArgusMethod} 列表进行匹配。
     * </p>
     *
     * @return 匹配到的 Web 请求方法，若未找到则返回 {@code null}
     */
    public static Method detectWebRequest() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        List<ArgusMethod> methods = ArgusCache.getMethods();
        for (ArgusMethod argusMethod : methods) {
            String signature = argusMethod.getMethod().getDeclaringClass().getName() + "." + argusMethod.getMethod().getName();
            Optional<StackTraceElement> first = Arrays.stream(stackTrace)
                    .filter(stackTraceElement -> (stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName()).equals(signature))
                    .findFirst();
            if (first.isPresent()) {
                return argusMethod.getMethod();
            }
        }
        return null;
    }

    /**
     * 在调用栈中查找 Mapper 或 Repository 接口方法。
     *
     * @param stackTrace 当前线程的调用栈
     * @return 识别到的 Mapper/Repository 方法信息，若未找到则返回 {@link MethodInfo#UNKNOWN}
     */
    private static MethodInfo findMapperOrRepositoryMethod(StackTraceElement[] stackTrace) {
        for (int i = 3; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            String className = element.getClassName();
            String methodName = element.getMethodName();

            // 跳过框架类和代理类
            if (isExcludedClass(className)) {
                continue;
            }

            String targetClassName = className;
            // 如果是代理类，尝试解析真实接口
            if (ProxyUtil.isProxyClass(className)) {
                targetClassName = resolveProxyInterfaceDynamic(className);
            }

            // 检查是否是Mapper/Repository接口
            if (isMapperOrRepositoryInterface(targetClassName)) {
                return new MethodInfo(targetClassName, methodName, getSimpleClassName(targetClassName));
            }
        }
        return MethodInfo.UNKNOWN;
    }

    /**
     * 在调用栈中查找 Service 层方法（作为降级方案）。
     *
     * @param stackTrace 当前线程的调用栈
     * @return 识别到的 Service 方法信息，若未找到则返回 {@link MethodInfo#UNKNOWN}
     */
    private static MethodInfo findServiceMethod(StackTraceElement[] stackTrace) {
        for (int i = 3; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            String className = element.getClassName();
            String methodName = element.getMethodName();

            if (isExcludedClass(className) || ProxyUtil.isProxyClass(className)) {
                continue;
            }

            // 检查是否是Service层方法
            if (isServiceLayerMethod(className, methodName)) {
                return new MethodInfo(className, methodName, getSimpleClassName(className));
            }
        }
        return MethodInfo.UNKNOWN;
    }

    /**
     * 解析代理类对应的真实接口（已废弃，未被使用）。
     * <p>保留此方法以备将来扩展，当前逻辑由 {@link #resolveProxyInterfaceDynamic(String)} 处理。</p>
     *
     * @param proxyClassName 代理类全限定名
     * @param methodName     方法名（未使用）
     * @return 接口方法信息，若解析失败则返回 {@link MethodInfo#UNKNOWN}
     */
    @Deprecated
    private static MethodInfo resolveProxyInterface(String proxyClassName, String methodName) {
        try {
            String interfaceName = PROXY_TO_INTERFACE_CACHE.get(proxyClassName);
            if (interfaceName != null) {
                return new MethodInfo(interfaceName, methodName, getSimpleClassName(interfaceName));
            }

            interfaceName = resolveProxyInterfaceDynamic(proxyClassName);
            if (interfaceName != null) {
                PROXY_TO_INTERFACE_CACHE.put(proxyClassName, interfaceName);
                return new MethodInfo(interfaceName, methodName, getSimpleClassName(interfaceName));
            }
        } catch (Exception e) {
            // 忽略解析异常
        }
        return MethodInfo.UNKNOWN;
    }

    /**
     * 动态解析代理类所实现的真实 Mapper/Repository 接口。
     * <p>支持 JDK 动态代理和 CGLIB 代理。</p>
     *
     * @param proxyClassName 代理类的全限定名
     * @return 真实接口的全限定名，若无法识别则返回 {@code null}
     */
    private static String resolveProxyInterfaceDynamic(String proxyClassName) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> proxyClass = Class.forName(proxyClassName, false, classLoader);

            // JDK 动态代理
            if (java.lang.reflect.Proxy.isProxyClass(proxyClass)) {
                Class<?>[] interfaces = proxyClass.getInterfaces();
                for (Class<?> interfaceClass : interfaces) {
                    String interfaceName = interfaceClass.getName();
                    if (isMapperOrRepositoryInterface(interfaceName)) {
                        return interfaceName;
                    }
                }
            }

            // CGLIB 代理：检查父类
            Class<?> superClass = proxyClass.getSuperclass();
            if (superClass != null && isMapperOrRepositoryInterface(superClass.getName())) {
                return superClass.getName();
            }

            // 检查所有实现的接口
            for (Class<?> interfaceClass : proxyClass.getInterfaces()) {
                String interfaceName = interfaceClass.getName();
                if (isMapperOrRepositoryInterface(interfaceName)) {
                    return interfaceName;
                }
            }

        } catch (Exception e) {
            // 忽略类加载或反射异常
        }
        return null;
    }

    /**
     * 判断给定类名是否属于 Mapper 或 Repository 接口。
     *
     * @param className 类的全限定名
     * @return {@code true} 如果是 Mapper/Repository 接口，否则 {@code false}
     */
    private static boolean isMapperOrRepositoryInterface(String className) {
        if (className == null) return false;

        String simpleName = getSimpleClassName(className);

        boolean hasMapperSuffix = MAPPER_SUFFIXES.stream()
                .anyMatch(simpleName::endsWith);

        boolean hasMapperPackage = className.contains(".mapper.") ||
                className.contains(".repository.") ||
                className.contains(".dao.");

        return hasMapperSuffix || hasMapperPackage;
    }

    /**
     * 判断是否为 Service 层的有效业务方法。
     *
     * @param className  类全限定名
     * @param methodName 方法名
     * @return {@code true} 如果是 Service 层且方法名符合业务前缀，否则 {@code false}
     */
    private static boolean isServiceLayerMethod(String className, String methodName) {
        if (className == null) return false;

        String simpleName = getSimpleClassName(className);
        boolean isServiceClass = simpleName.endsWith("Service") ||
                simpleName.endsWith("ServiceImpl") ||
                className.contains(".service.");

        boolean isBusinessMethod = INCLUDE_METHOD_PREFIXES.stream()
                .anyMatch(prefix -> methodName.toLowerCase().startsWith(prefix));

        return isServiceClass && isBusinessMethod;
    }

    /**
     * 判断类是否应被排除（基于包名前缀）。
     *
     * @param className 类全限定名
     * @return {@code true} 如果应被排除，否则 {@code false}
     */
    private static boolean isExcludedClass(String className) {
        return EXCLUDE_PACKAGES.stream().anyMatch(className::startsWith);
    }

    /**
     * 从全限定类名中提取简单类名（不含包路径）。
     *
     * @param className 全限定类名
     * @return 简单类名
     */
    private static String getSimpleClassName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    // ==================== 配置 API ====================

    /**
     * 添加需显式包含的包名（用于覆盖默认排除规则）。
     *
     * @param packageName 包名（如 "com.example.myapp"）
     */
    public static void addIncludePackage(String packageName) {
        INCLUDE_PACKAGES.add(packageName.endsWith(".") ? packageName : packageName + ".");
    }

    /**
     * 添加需排除的包名。
     *
     * @param packageName 包名（如 "com.example.util"）
     */
    public static void addExcludePackage(String packageName) {
        EXCLUDE_PACKAGES.add(packageName.endsWith(".") ? packageName : packageName + ".");
    }

    /**
     * 添加被视为 DAO/Repository 的类名后缀。
     *
     * @param suffix 类名后缀（如 "CustomDao"）
     */
    public static void addIncludeClassSuffix(String suffix) {
        INCLUDE_CLASS_SUFFIXES.add(suffix);
    }

    /**
     * 添加被视为业务方法的方法名前缀。
     *
     * @param prefix 方法名前缀（如 "fetch"）
     */
    public static void addIncludeMethodPrefix(String prefix) {
        INCLUDE_METHOD_PREFIXES.add(prefix.toLowerCase());
    }

    /**
     * 手动注册代理类到接口的映射关系，用于加速或绕过动态解析。
     *
     * @param proxyClassName   代理类全限定名
     * @param interfaceName    真实接口全限定名
     */
    public static void registerProxyMapping(String proxyClassName, String interfaceName) {
        PROXY_TO_INTERFACE_CACHE.put(proxyClassName, interfaceName);
    }

    /**
     * 从全限定类名中提取包名。
     * <div>
     * 示例：
     * <ul>
     *   <li>{@code "java.lang.String"} → {@code "java.lang"}</li>
     *   <li>{@code "com.example.MyClass"} → {@code "com.example"}</li>
     *   <li>{@code "MyClass"}（无包） → {@code ""}</li>
     *   <li>{@code null} 或空字符串 → {@code ""}</li>
     * </ul>
     * </div>
     *
     * @param fullyQualifiedClassName 全限定类名，如 "com.example.service.UserService"
     * @return 包名；若无包名或输入无效，则返回空字符串
     */
    public static String extractPackageName(String fullyQualifiedClassName) {
        if (fullyQualifiedClassName == null || fullyQualifiedClassName.isEmpty()) {
            return "";
        }

        int lastDotIndex = fullyQualifiedClassName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            // 没有点，说明是默认包（无包名）
            return "";
        }

        return fullyQualifiedClassName.substring(0, lastDotIndex);
    }

    /**
     * 表示检测到的方法信息的不可变值对象。
     * <p>提供方法全名、类名、方法名等访问接口。</p>
     */
    public static class MethodInfo {

        /**
         * 表示未知方法的常量实例。
         */
        public static final MethodInfo UNKNOWN = new MethodInfo("Unknown", "unknown", "Unknown");

        private final String packageName;
        private final String className;
        private final String methodName;
        private final String simpleClassName;

        /**
         * 构造方法。
         *
         * @param className       类全限定名
         * @param methodName      方法名
         * @param simpleClassName 简单类名（不含包路径）
         */
        public MethodInfo(String className, String methodName, String simpleClassName) {
            this.packageName = extractPackageName(className);
            this.className = className;
            this.methodName = methodName;
            this.simpleClassName = simpleClassName;
        }

        /**
         * 获取格式为 {@code SimpleClassName.methodName} 的方法标识。
         *
         * @return 方法标识字符串
         */
        public String getFullMethodName() {
            return simpleClassName + "." + methodName;
        }

        public String getPackageName() {
            return packageName;
        }

        /**
         * 获取类的全限定名。
         *
         * @return 类全限定名
         */
        public String getClassName() {
            return className;
        }

        /**
         * 获取方法名。
         *
         * @return 方法名
         */
        public String getMethodName() {
            return methodName;
        }

        /**
         * 获取简单类名（不含包路径）。
         *
         * @return 简单类名
         */
        public String getSimpleClassName() {
            return simpleClassName;
        }

        /**
         * 判断是否为未知方法。
         *
         * @return {@code true} 如果是未知方法，否则 {@code false}
         */
        public boolean isUnknown() {
            return this == UNKNOWN || "Unknown".equals(className);
        }

        /**
         * 判断该方法是否属于 Mapper 或 Repository 接口。
         *
         * @return {@code true} 如果是，否则 {@code false}
         */
        public boolean isMapperOrRepository() {
            return isMapperOrRepositoryInterface(className);
        }

        @Override
        public String toString() {
            return getFullMethodName();
        }
    }
}