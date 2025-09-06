package githubcew.arguslog.monitor.trace.buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buddy 增强管理器
 *
 * @author chenenwei
 */
public class BuddyProxyManager {

    private static final Logger log = LoggerFactory.getLogger(BuddyProxyManager.class);

    // 修改的类列表, key: 类名, value: 类
    private static final Map<String, Class<?>> modifiedClasses = new ConcurrentHashMap<>(10);

    //修改前字节码数组, key: 类名, value: 原字节数组
    private static final Map<String, byte[]> originalBytecodes = new ConcurrentHashMap<>(10);

    // 修改过的字节数组,key: 类名, value: 修改过的字节数组
    private static final Map<String, byte[]> modifiedBytecodes = new ConcurrentHashMap<>(10);

    // 修改的类列表, key: 方法key(类名.方法名.参数类型.拦截器), value: 是否已添加拦截器
    private static final Map<String, Boolean> modifiedMethodWithAdvice = new ConcurrentHashMap<>(10);

    // key 对应修改的类列表, key: 自定义key,  value: 修改的类列表
    private static final Map<String, Set<Class<?>>> modifiedClassesWithKey = new ConcurrentHashMap<>(10);

    // instrumentation
    private static Instrumentation instrumentation;

    private static final String DEFAULT_KEY = "default";

    /**
     * 初始化方法
     */
    public static void init() {

        if (!Objects.isNull(instrumentation)) {
            return;
        }
        instrumentation = ByteBuddyAgent.install();
        if (log.isDebugEnabled()) {
            log.debug("Argus =>  instrumentation init finished...");
        }
    }

    /**
     * 为指定类的增加方法
     *
     * @param key key
     * @param targetClass 类
     * @param advice      拦截器
     */
    public static void enhanceClass(String key, Class<?> targetClass, Class<?> advice) {
        enhanceMethods(key, targetClass, ElementMatchers.isMethod(), advice);
    }

    /**
     * 增强方法
     *
     * @param key key
     * @param targetClass 类型
     * @param methodName  方法名
     * @param advice      拦截器
     */
    public static void enhanceMethod(String key, Class<?> targetClass, String methodName, Class<?> advice) {

        enhanceMethods(key, targetClass, MethodMatchers.named(methodName), advice);
    }

    /**
     * 增强方法
     *
     * @param key key
     * @param targetClass 类型
     * @param methodNames 方法名
     * @param advice      拦截器
     */
    public static void enhanceMethods(String key,
                                      Class<?> targetClass,
                                      List<String> methodNames,
                                      Class<?> advice) {

        enhanceMethods(key, targetClass, MethodMatchers.namedIn(methodNames), advice);
    }

    /**
     * 增强方法
     *
     * @param key key
     * @param targetClass  目标类
     * @param methodMatcher 方法匹配器
     * @param advice 拦截器
     */
    private static void enhanceMethods(String key,
                                       Class<?> targetClass,
                                       ElementMatcher<? super MethodDescription> methodMatcher,
                                       Class<?> advice) {

        String className = targetClass.getName();

        try {
            // 保存原始字节码
            if (!originalBytecodes.containsKey(className)) {
                originalBytecodes.put(className, getClassBytes(targetClass));
                if (log.isDebugEnabled()) {
                    log.debug("Argus =>  Save original bytes for class: {}", className);
                }
            }

            // 使用 Byte Buddy Advice 进行方法增强
            DynamicType.Unloaded<?> unloadedType = new ByteBuddy()
                    .rebase(targetClass)
                    .visit(Advice.to(advice).on(methodMatcher))
                    .make();
            if (log.isDebugEnabled()) {
                saveClassToFile(unloadedType.getBytes(), className + "-after.class");
            }

            unloadedType.load(
                    targetClass.getClassLoader(),
                    ClassReloadingStrategy.fromInstalledAgent(ClassReloadingStrategy.Strategy.RETRANSFORMATION)
            );

            modifiedClasses.put(className, targetClass);

            modifiedClassesWithKey.putIfAbsent(key, new HashSet<>());
            modifiedClassesWithKey.get(key).add(targetClass);

            if (log.isDebugEnabled()) {
                log.debug("Argus =>  Enhanced class: {}", className);
            }

        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("Argus =>  Error enhancing class: {}", className, e);
            }
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 恢复指定类的原始实现
     *
     * @param targetClass 类
     */
    public static void revertClass(Class<?> targetClass) {
        String className = targetClass.getName();

        if (!modifiedClasses.containsKey(className)) {
            return;
        }

        try {
            byte[] originalBytes = originalBytecodes.get(className);
            if (originalBytes != null) {
                // 使用原始字节码恢复类
                instrumentation.redefineClasses(
                        new java.lang.instrument.ClassDefinition(targetClass, originalBytes)
                );

                modifiedClasses.remove(className);
                originalBytecodes.remove(className);

                // 移除类修改的方法
                modifiedMethodWithAdvice.keySet().removeIf(methodKey -> methodKey.startsWith(className));

                if (log.isDebugEnabled()) {
                    log.debug("Argus =>  Restored class: {}", className);
                }
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("Argus =>  Error restoring class: {}", className, e);
            }
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 恢复指定key的类列表的原始实现
     * @param key key
     */
    public static void revertClassWithKey(String key) {

        if (!modifiedClassesWithKey.containsKey(key)) {
            return;
        }

        Set<Class<?>> classes = modifiedClassesWithKey.get(key);
        for (Class<?> targetClass : classes) {
            String className = targetClass.getName();

            try {
                byte[] originalBytes = originalBytecodes.get(className);
                if (originalBytes != null) {
                    // 使用原始字节码恢复类
                    instrumentation.redefineClasses(
                            new java.lang.instrument.ClassDefinition(targetClass, originalBytes)
                    );

                    modifiedClasses.remove(className);
                    originalBytecodes.remove(className);

                    // 移除类修改的方法
                    modifiedMethodWithAdvice.keySet().removeIf(methodKey -> methodKey.startsWith(className));

                    if (log.isDebugEnabled()) {
                        log.debug("Argus =>  Restored class: {}", className);
                    }
                }

            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.error("Argus =>  Error restoring class: {}", className, e);
                }
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        modifiedClassesWithKey.remove(key);
    }

    /**
     * 恢复所有修改的类
     */
    public static void revertAllClasses() {
        List<String> classNames = new ArrayList<>(modifiedClasses.keySet());
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                revertClass(clazz);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.error("Argus =>  Error restoring class: {}", className, e);
                }
                e.printStackTrace();
            }
        }

        modifiedClassesWithKey.clear();
    }

    /**
     * 获取类的字节码
     */
    private static byte[] getClassBytes(Class<?> clazz) {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        InputStream is = null;
        try {
            is = clazz.getClassLoader().getResourceAsStream(resourceName);
            if (is == null) {
                return new byte[0];
            }

            // 使用 ByteArrayOutputStream 累积所有字节
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192]; // 8KB 缓冲区
            int bytesRead;
            while ((bytesRead = is.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            return buffer.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 检查类是否已被修改
     *
     * @param targetClass 类
     * @return 结果
     */
    public static boolean isClassModified(Class<?> targetClass) {
        return modifiedClasses.containsKey(targetClass.getName());
    }

    /**
     * 保存修改的方法
     *
     * @param targetClass 类
     * @param methodNames 方法名列表
     * @param advice      拦截器
     */
    private static void saveModifiedMethod(Class<?> targetClass, List<String> methodNames, Class<?> advice) {

        if (Objects.isNull(methodNames) || methodNames.isEmpty()) {
            return;
        }
        Method[] declaredMethods = targetClass.getDeclaredMethods();
        for (Method method : declaredMethods) {
            if (methodNames.contains(method.getName())) {
                modifiedMethodWithAdvice.putIfAbsent(generateSignature(method) + "#" + advice.getName(), true);
            }
        }
    }

    /**
     * 方法是否增强
     *
     * @param targetClass 类
     * @param methodName  方法名
     * @param advice      拦截器
     * @return 结果
     */
    private static boolean methodIsEnhanced(Class<?> targetClass, String methodName, Class<?> advice) {
        Method[] declaredMethods = targetClass.getDeclaredMethods();
        for (Method method : declaredMethods) {
            if (method.getName().equals(methodName)) {
                return modifiedMethodWithAdvice.containsKey(generateSignature(method) + "#" + advice.getName());
            }
        }
        return false;
    }

    /**
     * 生成指定格式的方法签名
     * 格式：全限定类名.方法名(参数类型1,参数类型2) 返回类型
     *
     * @param method 方法
     * @return 方法签名
     */
    public static String generateSignature(Method method) {
        return method.getDeclaringClass().getName() +
                "." +
                method.getName() +
                getParameterTypes(method) +
                " " +
                method.getReturnType().getSimpleName();
    }

    /**
     * 获取参数类型
     *
     * @param method 方法
     * @return 参数类型
     */
    private static String getParameterTypes(Method method) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 0) {
            return "()";
        }

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(params[i].getSimpleName());
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 获取所有已修改的类名
     *
     * @return 类名
     */
    public static Set<String> getModifiedClasses() {
        return Collections.unmodifiableSet(modifiedClasses.keySet());
    }

    /**
     * 方法匹配器工具方法
     */
    public static class MethodMatchers {

        /**
         * 匹配以特定前缀开头的方法
         *
         * @param prefix 前缀
         * @return 匹配器
         */
        public static ElementMatcher<MethodDescription> nameStartsWith(String prefix) {
            return ElementMatchers.nameStartsWith(prefix);
        }

        /**
         * 匹配以特定后缀结尾的方法
         *
         * @param suffix 后缀
         * @return 匹配器
         */
        public static ElementMatcher<MethodDescription> nameEndsWith(String suffix) {
            return ElementMatchers.nameEndsWith(suffix);
        }

        /**
         * 匹配包含特定字符串的方法
         *
         * @param text 字符串
         * @return 匹配器
         */
        public static ElementMatcher<MethodDescription> nameContains(String text) {
            return ElementMatchers.nameContains(text);
        }

        /**
         * 匹配特定名称的方法
         *
         * @param name 方法名
         * @return 匹配器
         */
        public static ElementMatcher<MethodDescription> named(String name) {
            return ElementMatchers.named(name);
        }

        /**
         * 匹配方法列表
         *
         * @param methodNames 方法列表
         * @return 匹配器
         */
        public static ElementMatcher.Junction<MethodDescription> namedIn(List<String> methodNames) {
            return methodNames.stream()
                    .<ElementMatcher.Junction<MethodDescription>>map(ElementMatchers::named)
                    .reduce(ElementMatcher.Junction::or)
                    .orElse(ElementMatchers.none());
        }

        /**
         * 匹配被特定注解标注的方法
         *
         * @param annotation 注解类
         * @return 匹配器
         */
        public static ElementMatcher<MethodDescription> annotatedWith(Class<? extends java.lang.annotation.Annotation> annotation) {
            return ElementMatchers.isAnnotatedWith(annotation);
        }

        /**
         * 匹配返回特定类型的方法
         *
         * @param returnType 返回值类型
         * @return 匹配器
         */
        public static ElementMatcher<MethodDescription> returns(Class<?> returnType) {
            return ElementMatchers.returns(returnType);
        }

        /**
         * 匹配公共方法
         *
         * @return 匹配器
         */
        public static ElementMatcher<MethodDescription> isPublic() {
            return ElementMatchers.isPublic();
        }

        /**
         * 匹配私有方法
         *
         * @return 匹配器
         */
        public static ElementMatcher<MethodDescription> isPrivate() {
            return ElementMatchers.isPrivate();
        }
    }

    public static void saveClassToFile(byte[] bytes, String fileName) throws Exception {

        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        fileOutputStream.write(bytes);
        fileOutputStream.close();
    }
}