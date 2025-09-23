package githubcew.arguslog.common.util;

import githubcew.arguslog.monitor.trace.buddy.BuddyProxyManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 字节码操作工具类，提供安全获取类字节码的能力。
 * <p>
 * 支持两种主要方式：
 * <ul>
 *   <li>通过 {@link Instrumentation#retransformClasses} 动态获取已加载类的当前字节码（适用于代理类、增强类）</li>
 *   <li>通过类路径资源加载原始字节码（适用于普通类）</li>
 * </ul>
 * <p>
 * 特别支持对 Spring CGLIB 代理、JDK 动态代理等动态生成类的原始字节码提取。
 * </p>
 *
 * @author chenenwei
 * @since 1.0.0
 */
public class ByteCodeUtil {

    /**
     * 通过 Java Agent Instrumentation 机制获取指定类的当前字节码。
     * <p>
     * 适用于获取已被增强或代理的类的运行时字节码（如 Spring AOP 代理、CGLIB 增强类等）。
     * <p>
     * 内部实现：
     * <ol>
     *   <li>注册临时 {@link ClassFileTransformer}</li>
     *   <li>调用 {@code retransformClasses} 触发字节码回调</li>
     *   <li>捕获并返回字节码数组</li>
     *   <li>自动清理临时 Transformer</li>
     * </ol>
     *
     * @param clazz 要获取字节码的目标类（不可为 null）
     * @return 类的字节码数组；若超时、类不可重转换或 Instrumentation 未初始化，则返回 {@code null}
     * @throws IllegalArgumentException 如果 clazz 为 null
     * @throws IllegalStateException    如果 Instrumentation 未设置
     */
    public static byte[] getClassBytecodeViaInstrumentation(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("目标类不能为 null");
        }

        Instrumentation instrumentation = BuddyProxyManager.getInstrumentation();
        if (instrumentation == null) {
            throw new IllegalStateException("Instrumentation 未初始化。请确保已通过 Java Agent 注入。");
        }

        AtomicReference<byte[]> bytecodeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 创建临时 Transformer，仅用于捕获目标类的字节码
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader,
                                    String className,
                                    Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain,
                                    byte[] classfileBuffer) {
                // 仅当重定义的类是我们目标类时才捕获
                if (classBeingRedefined != null && classBeingRedefined.equals(clazz)) {
                    bytecodeRef.set(classfileBuffer.clone()); // 克隆避免外部修改
                    latch.countDown();
                }
                return null; // 不修改字节码
            }
        };

        try {
            // 注册 Transformer（可重转换模式）
            instrumentation.addTransformer(transformer, true);

            // 触发重转换，JVM 会回调 transform 方法
            instrumentation.retransformClasses(clazz);

            // 等待字节码捕获，最多 5 秒
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                System.err.println("[ByteCodeUtil] 获取字节码超时: " + clazz.getName());
                return null;
            }

            return bytecodeRef.get();

        } catch (UnmodifiableClassException e) {
            System.err.println("[ByteCodeUtil] 类不可重转换: " + clazz.getName());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            System.err.println("[ByteCodeUtil] 等待字节码时被中断: " + clazz.getName());
            return null;
        } finally {
            // 重要：移除临时 Transformer，避免内存泄漏
            instrumentation.removeTransformer(transformer);
        }
    }

    /**
     * 安全获取类的字节码，自动尝试多种策略：
     * <ol>
     *   <li>从类路径资源加载（适用于普通类）</li>
     *   <li>若为代理类，则尝试加载其原始类/接口的字节码</li>
     * </ol>
     * <p>
     * 若所有策略均失败，返回 {@code null} 并打印错误日志。
     *
     * @param clazz 目标类
     * @return 类的原始字节码数组，失败返回 {@code null}
     */
    public static byte[] getClassBytesSafe(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        try {
            // 策略1：从类路径资源加载
            byte[] bytes = getClassBytesFromResource(clazz);
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }

            // 策略2：若为代理类，尝试获取原始类字节码
            bytes = getOriginalClassBytesForProxy(clazz);
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }

            // 所有策略失败
            throw new IOException("无法通过任何策略获取类字节码: " + clazz.getName());

        } catch (Exception e) {
            System.err.println("[ByteCodeUtil] 获取字节码失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从类路径资源中加载类的原始字节码（.class 文件）。
     * <p>
     * 适用于未被动态增强的普通类。
     * <p>
     * 自动排除动态生成类（如 CGLIB、JDK 代理），避免无效读取。
     *
     * @param clazz 目标类
     * @return 字节码数组，若类为动态生成或资源不存在则返回 {@code null}
     * @throws IOException IO 异常
     */
    private static byte[] getClassBytesFromResource(Class<?> clazz) throws IOException {
        String className = clazz.getName();

        // 排除常见动态生成类
        if (isDynamicGeneratedClass(className)) {
            return null;
        }

        // 转换为资源路径
        String classPath = className.replace('.', '/') + ".class";
        ClassLoader classLoader = clazz.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }

        try (InputStream is = classLoader.getResourceAsStream(classPath)) {
            if (is == null) {
                return null;
            }

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; // 增大缓冲区提高性能
            int length;
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }
            return os.toByteArray();
        }
    }

    /**
     * 判断类名是否为动态生成类（如代理、增强类）。
     * <p>
     * 匹配模式包括：
     * <ul>
     *   <li>Spring CGLIB 代理：{@code $$EnhancerBySpringCGLIB$$}</li>
     *   <li>JDK 动态代理：{@code $Proxy}</li>
     *   <li>FastClass：{@code $$FastClassBySpringCGLIB$$}</li>
     *   <li>其他动态类：{@code $$[0-9a-f]+}（如 Mockito、Hibernate 代理）</li>
     * </ul>
     *
     * @param className 类全限定名
     * @return 如果是动态生成类则返回 {@code true}
     */
    private static boolean isDynamicGeneratedClass(String className) {
        return className.contains("$$EnhancerBySpringCGLIB$$") ||
                className.contains("$Proxy") ||
                className.contains("$$FastClassBySpringCGLIB$$") ||
                className.matches(".*\\$\\$[0-9a-fA-F]+$"); // 匹配十六进制后缀的动态类
    }

    /**
     * 对于动态代理类，尝试获取其原始类（CGLIB）或接口（JDK）的字节码。
     *
     * @param clazz 代理类
     * @return 原始类/接口的字节码数组，若无法获取则返回 {@code null}
     * @throws IOException IO 异常
     */
    private static byte[] getOriginalClassBytesForProxy(Class<?> clazz) throws IOException {
        if (isCGLIBProxy(clazz)) {
            // CGLIB 代理：父类为原始类
            Class<?> originalClass = clazz.getSuperclass();
            if (originalClass != null && originalClass != Object.class) {
                return getClassBytesFromResource(originalClass);
            }
        } else if (isJDKProxy(clazz)) {
            // JDK 代理：实现的接口为原始契约
            Class<?>[] interfaces = clazz.getInterfaces();
            if (interfaces.length > 0) {
                // 优先返回第一个接口的字节码
                return getClassBytesFromResource(interfaces[0]);
            }
        }
        return null;
    }

    /**
     * 判断是否为 CGLIB 代理类（特指 Spring CGLIB 增强）。
     *
     * @param clazz 类对象
     * @return 如果是 CGLIB 代理类则返回 {@code true}
     */
    private static boolean isCGLIBProxy(Class<?> clazz) {
        return clazz.getName().contains("$$EnhancerBySpringCGLIB$$");
    }

    /**
     * 判断是否为 JDK 动态代理类。
     *
     * @param clazz 类对象
     * @return 如果是 JDK 动态代理类则返回 {@code true}
     * @see java.lang.reflect.Proxy#isProxyClass(Class)
     */
    private static boolean isJDKProxy(Class<?> clazz) {
        return java.lang.reflect.Proxy.isProxyClass(clazz);
    }
}