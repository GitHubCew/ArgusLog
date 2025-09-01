package githubcew.arguslog.monitor.trace;

import githubcew.arguslog.monitor.trace.asm.AsmEnhancerVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArgusClassRedefinerManager {

    private final static Logger log = LoggerFactory.getLogger(ArgusClassRedefinerManager.class);

    private static final Map<Class<?>, byte[]> originalBytecode = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> enhancedMethods = new ConcurrentHashMap<>();
    // 监听方法-增强的类
    private static final Map<String, List<Class<?>>> enhancedClasses = new ConcurrentHashMap<>();

    private static final ArgusClassRedefiner classRedefiner;

    static {
        classRedefiner = new ArgusClassRedefiner();
    }

    /**
     * 增强方法
     */
    public static void enhanceMethod(String methodKey, Class<?> targetClass, String methodName) throws Exception {
        enhanceMethods(methodKey, targetClass, Collections.singletonList(methodName));
    }

    /**
     * 增强多个方法
     */
    public static void enhanceMethods(String methodKey, Class<?> targetClass, List<String> methodNames) throws Exception {

        if (targetClass.isInterface()) {
            return;
        }

        if (Proxy.isProxyClass(targetClass)) {
            return ;
        }

        // 保存原始字节码（如果尚未保存）
        originalBytecode.computeIfAbsent(targetClass, k -> {
            try {
                return getClassBytes(targetClass);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get class bytes", e);
            }
        });

        // 获取当前字节码（可能是已经被增强过的）
        byte[] currentBytes = getClassBytes(targetClass);

        String internalClassName = targetClass.getName().replace(".", "/");
        // 增强字节码
        byte[] enhancedBytes = AsmEnhancerVisitor.modify(currentBytes, internalClassName, methodNames);

        // 重定义
        classRedefiner.redefine(targetClass.getName(), enhancedBytes);

        // debug 保存重定义后的字节码文件
        if (log.isDebugEnabled()) {
            saveClassToFile(enhancedBytes, targetClass.getName() + "-after.class");

        }
        // 记录增强的方法
        String className = targetClass.getName();
        enhancedMethods.computeIfAbsent(className, k -> new HashSet<>()).addAll(methodNames);

        // 记录监控方法增强的类集合
        enhancedClasses.computeIfAbsent(methodKey, k -> new ArrayList<>()).add(targetClass);

        if (log.isDebugEnabled()) {
            log.info("Argus redefine class => {}", className);
        }
    }


    /**
     * 撤销增强（恢复原始字节码）
     */
    public static void revertEnhancement(Class<?> targetClass) throws Exception {

        byte[] originalBytes = originalBytecode.get(targetClass);
        if (originalBytes != null) {
            classRedefiner.redefine(targetClass.getName(), originalBytes);
            enhancedMethods.remove(targetClass.getName());
            originalBytecode.remove(targetClass);
            if (log.isDebugEnabled()) {
                log.info("Argus reverted class => {}", targetClass.getName());
            }
        }
    }

    /**
     * 撤销全部增强（恢复原始字节码）
     */
    public static void revertAllEnhancement() throws Exception {
        for (Class<?> targetClass : originalBytecode.keySet()) {
            revertEnhancement(targetClass);
            originalBytecode.remove(targetClass);
        }
        if (log.isDebugEnabled()) {
            log.info("Argus reverted all class...");
        }
    }

    public static void revertEnhancement(String methodKey) throws Exception {

        if (Objects.isNull(methodKey) || methodKey.isEmpty()) {
            return;
        }
        List<Class<?>> enhancedClasses = ArgusClassRedefinerManager.enhancedClasses.get(methodKey);
        if (enhancedClasses != null) {
            for (Class<?> enhancedClass : enhancedClasses) {
                revertEnhancement(enhancedClass);
            }
            ArgusClassRedefinerManager.enhancedClasses.remove(methodKey);
        }
        if (log.isDebugEnabled()) {
            log.info("Argus reverted method => {}", enhancedClasses);
        }
    }

    /**
     * 检查方法是否已被增强
     */
    public static boolean isMethodEnhanced(Class<?> targetClass, String methodName) {
        Set<String> methods = enhancedMethods.get(targetClass.getName());
        return methods != null && methods.contains(methodName);
    }

    /**
     * 获取已增强的方法列表
     */
    public static Set<String> getEnhancedMethods(Class<?> targetClass) {
        return enhancedMethods.getOrDefault(targetClass.getName(), Collections.emptySet());
    }

    /**
     * 获取已增强的方法列表
     */
    public static Set<String> getEnhancedMethods() {
        return enhancedMethods.keySet();
    }


    /**
     * 获取类字节数组
     *
     * @param clazz 类
     * @return 字节数字
     * @throws Exception 异常
     */
    public static byte[] getClassBytes(Class<?> clazz) throws Exception {

        // 代理类


        // 其他类
        String className = clazz.getName();
        String classPath = className.replace('.', '/') + ".class";

        try (InputStream is = clazz.getClassLoader().getResourceAsStream(classPath);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            if (is == null) {
                throw new RuntimeException("Class not found: " + classPath);
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            return baos.toByteArray();
        }
    }

    /**
     * 从代理对象获取目标类
     *
     * @param bean 代理对象
     * @return 目标类
     */
    private static Class<?> getTargetClassFromProxy(Object bean) {
        // 简化处理，实际需要检查Spring AOP代理
        if (bean.getClass().getName().contains("$Proxy")) {
            return bean.getClass().getSuperclass();
        }
        return bean.getClass();
    }

    public static void saveClassToFile(byte[] bytes, String fileName) throws Exception {

        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        fileOutputStream.write(bytes);
        fileOutputStream.close();
    }

    /**
     * 使用反射调用ProxyGenerator生成字节码
     */
    private static byte[] generateProxyClassBytes(Class<?> proxyClass) {
        try {
            // 获取InvocationHandler
            InvocationHandler handler = Proxy.getInvocationHandler(proxyClass);

            // 获取接口
            Class<?>[] interfaces = proxyClass.getInterfaces();

            // 使用反射调用ProxyGenerator
            Class<?> proxyGeneratorClass = Class.forName("java.lang.reflect.ProxyGenerator");
            Method generateProxyClassMethod = proxyGeneratorClass.getDeclaredMethod(
                    "generateProxyClass", String.class, Class[].class, Integer.TYPE);
            generateProxyClassMethod.setAccessible(true);

            return (byte[]) generateProxyClassMethod.invoke(null,
                    proxyClass.getName(), interfaces, Modifier.PUBLIC);

        } catch (Exception e) {
            throw new RuntimeException("无法生成代理类字节码", e);
        }
    }


    /**
     * 生成新的代理类字节码（Java 8 兼容版本）
     */
    private static byte[] generateNewProxyClassBytes(Class<?> proxyClass) {
        try {
            Class<?>[] interfaces = proxyClass.getInterfaces();

            // Java 8 中 ProxyGenerator 在 sun.misc 包中
            Class<?> proxyGeneratorClass = Class.forName("sun.misc.ProxyGenerator");
            Method generateProxyClassMethod = proxyGeneratorClass.getDeclaredMethod(
                    "generateProxyClass", String.class, Class[].class, int.class);
            generateProxyClassMethod.setAccessible(true);

            // 生成字节码 - Java 8 需要传递访问标志
            return (byte[]) generateProxyClassMethod.invoke(null,
                    proxyClass.getName(), interfaces, java.lang.reflect.Modifier.PUBLIC);

        } catch (Exception e) {
            throw new RuntimeException("生成代理类字节码失败", e);
        }
    }
}