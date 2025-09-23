package githubcew.arguslog.common.util;

import githubcew.arguslog.core.account.ArgusUserProvider;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Java 类字节码反编译工具类，基于 CFR 反编译器和 ASM 字节码操作框架。
 * <p>
 * 支持功能：
 * <ul>
 *   <li>完整类反编译</li>
 *   <li>指定方法反编译（支持方法过滤）</li>
 *   <li>代理类字节码提取与反编译</li>
 *   <li>在方法前插入源码行号注释</li>
 *   <li>仅提取方法签名（无方法体）</li>
 * </ul>
 * <p>
 * 适用于调试、日志增强、运行时诊断等场景。
 * 依赖 CFR 0.152+，兼容 Java 8+。
 * </p>
 *
 * @author chenenwei
 * @since 1.0.0
 */
public class DecompilerUtil {

    private static final Logger log = LoggerFactory.getLogger(DecompilerUtil.class);

    /**
     * CFR 反编译器全局配置选项。
     * <p>
     * 默认配置支持：
     * <ul>
     *   <li>显示 UTF-8 字符</li>
     *   <li>保留 Lambda 表达式</li>
     *   <li>允许匿名类</li>
     *   <li>显示内部类</li>
     * </ul>
     */
    public static final Map<String, String> OPTIONS = new HashMap<>();

    static {
        OPTIONS.put("hideutf", "false");
        OPTIONS.put("removeboilerplate", "false");
        OPTIONS.put("forbidanonymousclasses", "false");
        OPTIONS.put("innerclasses", "true");
        OPTIONS.put("showinf", "true");
        OPTIONS.put("decodelambdas", "true");
        OPTIONS.put("override", "true");
    }

    /**
     * 自定义类文件源，支持从 ClassLoader 和文件系统加载字节码。
     */
    private static final ClassFileSource CLASS_FILE_SOURCE = new ClassFileSource() {
        @Override
        public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
            // 无操作
        }

        @Override
        public Collection<String> addJar(String jarPath) {
            return Collections.emptyList();
        }

        @Override
        public String getPossiblyRenamedPath(String path) {
            return path;
        }

        @Override
        public Pair<byte[], String> getClassFileContent(String path) throws IOException {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                return Pair.make(bytes, path);
            }

            InputStream is = null;
            ClassLoader[] loaders = {
                    Thread.currentThread().getContextClassLoader(),
                    DecompilerUtil.class.getClassLoader(),
                    ClassLoader.getSystemClassLoader()
            };

            for (ClassLoader loader : loaders) {
                if (loader != null) {
                    is = loader.getResourceAsStream(path);
                    if (is != null) {
                        break;
                    }
                }
            }

            if (is == null) {
                return null;
            }

            try (InputStream in = is) {
                byte[] bytes = toByteArray(in);
                return Pair.make(bytes, path);
            }
        }
    };

    // ========== 公共反编译入口 ==========

    /**
     * 根据类的全限定名反编译整个类。
     *
     * @param fullClassName 类的全限定名，如 "java.lang.String"
     * @param onlyMethod    是否仅返回方法签名（无方法体）
     * @return 反编译后的 Java 源码
     * @throws RuntimeException 类未找到或反编译失败
     */
    public static String decompileClass(String fullClassName, boolean onlyMethod) {
        Objects.requireNonNull(fullClassName, "类名不能为空");
        try {
            Class<?> targetClass = Class.forName(fullClassName);
            return decompileClass(targetClass, onlyMethod);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("类未找到: " + fullClassName, e);
        }
    }

    /**
     * 根据 {@link Class} 对象反编译整个类。
     *
     * @param clazz       要反编译的类
     * @param onlyMethod  是否仅返回方法签名（无方法体）
     * @return 反编译后的 Java 源码
     * @throws RuntimeException 无法获取字节码或反编译失败
     */
    public static String decompileClass(Class<?> clazz, boolean onlyMethod) {
        Objects.requireNonNull(clazz, "类对象不能为空");
        try {
            byte[] bytecode = getBytecode(clazz);
            if (bytecode == null || bytecode.length == 0) {
                return "";
            }
            return decompileBytecode(bytecode, clazz.getName(), onlyMethod);
        } catch (Exception e) {
            throw new RuntimeException("获取字节码失败: " + clazz.getName(), e);
        }
    }

    /**
     * 根据对象实例反编译其类。
     *
     * @param object    对象实例
     * @param isProxy   是否为代理对象（影响字节码获取方式）
     * @param onlyMethod 是否仅返回方法签名（无方法体）
     * @return 反编译后的 Java 源码
     * @throws RuntimeException 反编译失败
     */
    public static String decompileClass(Object object, boolean isProxy, boolean onlyMethod) {
        Objects.requireNonNull(object, "对象不能为空");
        try {
            return decompileClassViaBean(object, isProxy, onlyMethod);
        } catch (Exception e) {
            throw new RuntimeException("反编译失败", e);
        }
    }

    /**
     * 根据对象实例反编译其类（内部实现）。
     *
     * @param bean      对象实例
     * @param isProxy   是否为代理对象
     * @param onlyMethod 是否仅返回方法签名
     * @return 反编译源码
     */
    public static String decompileClassViaBean(Object bean, boolean isProxy, boolean onlyMethod) {
        Objects.requireNonNull(bean, "Bean 不能为空");
        try {
            byte[] bytecode = getBytecode(bean, isProxy);
            if (bytecode == null || bytecode.length == 0) {
                return "";
            }
            return decompileBytecode(bytecode, bean.getClass().getName(), onlyMethod);
        } catch (Exception e) {
            throw new RuntimeException("获取字节码失败: " + bean.getClass().getName(), e);
        }
    }

    // ========== 方法级反编译 ==========

    /**
     * 反编译指定类中的多个方法（完整方法体）。
     *
     * @param fullClassName 类全限定名
     * @param methodNames   方法名列表（支持重载，需精确匹配）
     * @return 包含指定方法的源码
     * @throws RuntimeException 类未找到或反编译失败
     */
    public static String decompileMethods(String fullClassName, List<String> methodNames) {
        Objects.requireNonNull(fullClassName, "类名不能为空");
        methodNames = Optional.ofNullable(methodNames).orElseGet(ArrayList::new);

        try {
            Class<?> targetClass = Class.forName(fullClassName);
            return decompileMethods(targetClass, methodNames);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("类未找到: " + fullClassName, e);
        }
    }

    /**
     * 反编译指定类中的多个方法（完整方法体）。
     *
     * @param targetClass 类对象
     * @param methodNames 方法名列表
     * @return 包含指定方法的源码
     * @throws RuntimeException 反编译失败
     */
    public static String decompileMethods(Class<?> targetClass, List<String> methodNames) {
        Objects.requireNonNull(targetClass, "类对象不能为空");
        methodNames = Optional.ofNullable(methodNames).orElseGet(ArrayList::new);

        try {
            byte[] originalBytecode = getBytecode(targetClass);
            if (originalBytecode == null || originalBytecode.length == 0) {
                return "";
            }

            byte[] filteredBytecode = filterMethods(originalBytecode, new HashSet<>(methodNames));
            String decompiled = decompileFilteredBytecode(filteredBytecode, targetClass.getName());
            return extractMethodsOnly(decompiled, methodNames);

        } catch (Exception e) {
            throw new RuntimeException("反编译方法失败: " + methodNames, e);
        }
    }

    /**
     * 反编译对象实例中指定的多个方法（完整方法体）。
     *
     * @param bean        对象实例
     * @param isProxy     是否为代理对象
     * @param methodNames 方法名列表
     * @return 包含指定方法的源码
     * @throws RuntimeException 反编译失败
     */
    public static String decompileMethods(Object bean, boolean isProxy, List<String> methodNames) {
        Objects.requireNonNull(bean, "对象不能为空");
        methodNames = Optional.ofNullable(methodNames).orElseGet(ArrayList::new);

        try {
            Class<?> targetClass = bean.getClass();
            byte[] originalBytecode = getBytecode(bean, isProxy);
            if (originalBytecode == null || originalBytecode.length == 0) {
                return "";
            }

            byte[] filteredBytecode = filterMethods(originalBytecode, new HashSet<>(methodNames));
            String decompiled = decompileFilteredBytecode(filteredBytecode, targetClass.getName());
            return extractMethodsOnly(decompiled, methodNames);

        } catch (Exception e) {
            throw new RuntimeException("反编译方法失败: " + methodNames, e);
        }
    }

    // ========== 字节码处理核心 ==========

    /**
     * 使用 CFR 反编译字节码，并注入行号注释。
     *
     * @param bytecode    类字节码
     * @param className   类全限定名（用于调试）
     * @param onlyMethod  是否仅返回方法签名
     * @return 反编译后的 Java 源码
     * @throws RuntimeException 反编译失败
     */
    private static String decompileBytecode(byte[] bytecode, String className, boolean onlyMethod) {
        if (bytecode == null || bytecode.length == 0) {
            return "";
        }

        File tempFile = null;
        final StringBuilder result = new StringBuilder();

        try {
            tempFile = File.createTempFile(className.replace('.', '_'), ".class");
            Files.write(tempFile.toPath(), bytecode);

            OutputSinkFactory sinkFactory = createSinkFactory(result);

            CfrDriver driver = new CfrDriver.Builder()
                    .withOutputSink(sinkFactory)
                    .withOptions(OPTIONS)
                    .withClassFileSource(CLASS_FILE_SOURCE)
                    .build();

            driver.analyse(Collections.singletonList(tempFile.getAbsolutePath()));

            String output = result.toString().trim();
            if (output.isEmpty()) {
                return "";
            }

            // 注入行号
            Map<String, Integer> methodLines = parseMethodLineNumbers(bytecode);
            output = insertLineNumbers(output, methodLines, className);

            // 移除包声明前的注释（CFR 有时会加）
            int packageIndex = output.indexOf("package ");
            if (packageIndex != -1) {
                output = output.substring(packageIndex);
            }

            return onlyMethod ? extractMethodSignaturesOnly(output) : output;

        } catch (Exception e) {
            log.warn("反编译失败: {}", className, e);
            throw new RuntimeException("反编译失败: " + className, e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (log.isDebugEnabled()) {
                    log.debug("删除临时文件: {}", tempFile.getAbsolutePath());
                }
                tempFile.delete();
            }
        }
    }

    /**
     * 创建 CFR 输出接收器。
     */
    private static OutputSinkFactory createSinkFactory(StringBuilder result) {
        return new OutputSinkFactory() {
            @Override
            public List<OutputSinkFactory.SinkClass> getSupportedSinks(
                    OutputSinkFactory.SinkType sinkType, Collection<OutputSinkFactory.SinkClass> collection) {
                return Arrays.asList(
                        OutputSinkFactory.SinkClass.STRING,
                        OutputSinkFactory.SinkClass.DECOMPILED
                );
            }

            @Override
            public <T> OutputSinkFactory.Sink<T> getSink(
                    OutputSinkFactory.SinkType sinkType, OutputSinkFactory.SinkClass sinkClass) {
                if (sinkType == OutputSinkFactory.SinkType.JAVA &&
                        (sinkClass == OutputSinkFactory.SinkClass.STRING ||
                                sinkClass == OutputSinkFactory.SinkClass.DECOMPILED)) {
                    return (OutputSinkFactory.Sink<T>) decompiled -> {
                        result.append(decompiled).append("\n");
                    };
                }
                return x -> {};
            }
        };
    }

    /**
     * 反编译过滤后的字节码（仅包含指定方法）。
     *
     * @param bytecode  过滤后的字节码
     * @param className 类名（用于临时文件命名）
     * @return 反编译源码
     * @throws RuntimeException 反编译失败
     */
    private static String decompileFilteredBytecode(byte[] bytecode, String className) {
        if (bytecode == null || bytecode.length == 0) {
            return "";
        }

        File tempFile = null;
        final StringBuilder result = new StringBuilder();

        try {
            tempFile = File.createTempFile("filtered_" + className.replace('.', '_'), ".class");
            Files.write(tempFile.toPath(), bytecode);

            OutputSinkFactory sinkFactory = createSinkFactory(result);

            CfrDriver driver = new CfrDriver.Builder()
                    .withOutputSink(sinkFactory)
                    .withOptions(OPTIONS)
                    .withClassFileSource(CLASS_FILE_SOURCE)
                    .build();

            driver.analyse(Collections.singletonList(tempFile.getAbsolutePath()));
            return result.toString();

        } catch (Exception e) {
            throw new RuntimeException("反编译过滤字节码失败", e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    // ========== 行号与方法提取 ==========

    /**
     * 使用 ASM 解析字节码，提取每个方法的第一行源码行号。
     *
     * @param bytecode 类字节码
     * @return 方法名到行号的映射（格式：methodName() -> lineNumber）
     */
    private static Map<String, Integer> parseMethodLineNumbers(byte[] bytecode) {
        Map<String, Integer> methodLines = new HashMap<>();
        try {
            ClassReader reader = new ClassReader(bytecode);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);

            for (MethodNode method : classNode.methods) {
                if (method.instructions == null) continue;

                for (int i = 0; i < method.instructions.size(); i++) {
                    AbstractInsnNode node = method.instructions.get(i);
                    if (node instanceof LineNumberNode) {
                        LineNumberNode ln = (LineNumberNode) node;
                        methodLines.put(method.name + "()", ln.line);
                        break; // 只取第一个行号
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析行号失败", e);
        }
        return methodLines;
    }

    /**
     * 在反编译结果中为每个方法插入行号注释。
     *
     * @param decompiledCode 反编译源码
     * @param methodLines    方法名到行号的映射
     * @param className      类名（用于调试）
     * @return 插入行号注释后的源码
     */
    private static String insertLineNumbers(String decompiledCode, Map<String, Integer> methodLines, String className) {
        if (decompiledCode == null || methodLines == null || methodLines.isEmpty()) {
            return decompiledCode;
        }

        StringBuilder result = new StringBuilder();
        String[] lines = decompiledCode.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (isMethodSignature(trimmed)) {
                String methodName = extractMethodName(trimmed);
                if (methodName != null) {
                    Integer lineNumber = methodLines.get(methodName + "()");
                    if (lineNumber != null) {
                        result.append("    // line ").append(lineNumber).append("\n");
                    }
                }
            }
            result.append(line).append("\n");
        }

        return result.toString();
    }

    /**
     * 从完整反编译结果中提取指定方法（完整方法体）。
     *
     * @param decompiledCode 完整反编译源码
     * @param methodNames    要提取的方法名列表
     * @return 仅包含指定方法的源码
     */
    private static String extractMethodsOnly(String decompiledCode, List<String> methodNames) {
        if (decompiledCode == null || decompiledCode.isEmpty() ||
                methodNames == null || methodNames.isEmpty()) {
            return decompiledCode;
        }

        Set<String> targetMethods = new HashSet<>(methodNames);
        StringBuilder result = new StringBuilder();
        String[] lines = decompiledCode.split("\n");

        boolean inTargetMethod = false;
        int braceLevel = 0;
        StringBuilder currentMethod = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            // 检测方法开始
            if (!inTargetMethod && isMethodSignature(trimmed)) {
                String methodName = extractMethodName(trimmed);
                if (methodName != null && targetMethods.contains(methodName)) {
                    inTargetMethod = true;
                    braceLevel = 0;
                    currentMethod.setLength(0);
                }
            }

            if (inTargetMethod) {
                currentMethod.append(line).append("\n");

                braceLevel += countChar(trimmed, '{');
                braceLevel -= countChar(trimmed, '}');

                if (braceLevel == 0) {
                    result.append(currentMethod);
                    inTargetMethod = false;
                }
            }
        }

        return result.toString();
    }

    /**
     * 从完整反编译结果中提取所有方法签名（无方法体）。
     *
     * @param decompiledCode 完整反编译源码
     * @return 所有方法签名，每行一个
     */
    private static String extractMethodSignaturesOnly(String decompiledCode) {
        if (decompiledCode == null || decompiledCode.isEmpty()) {
            return "";
        }

        return Arrays.stream(decompiledCode.split("\n"))
                .map(String::trim)
                .filter(DecompilerUtil::isMethodSignature)
                .map(DecompilerUtil::extractMethodSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    // ========== 工具方法 ==========

    /**
     * 判断是否为方法签名行。
     */
    private static boolean isMethodSignature(String line) {
        if (line == null || line.isEmpty()) return false;
        return (line.startsWith("public ") || line.startsWith("private ") ||
                line.startsWith("protected ") || line.startsWith("default ")) &&
                line.contains("(") && line.contains(")");
    }

    /**
     * 从方法声明行中提取方法名。
     */
    private static String extractMethodName(String line) {
        if (line == null) return null;
        int start = line.indexOf(' ');
        if (start == -1) return null;
        int end = line.indexOf('(', start);
        if (end == -1) return null;
        String[] parts = line.substring(start + 1, end).trim().split("\\s+");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    /**
     * 提取方法签名（直到第一个 '{' 或 ';'）。
     */
    private static String extractMethodSignature(String line) {
        if (line == null) return null;
        int braceIndex = line.indexOf('{');
        int semicolonIndex = line.indexOf(';');
        int endIndex = line.length();

        if (braceIndex != -1 && semicolonIndex != -1) {
            endIndex = Math.min(braceIndex, semicolonIndex);
        } else if (braceIndex != -1) {
            endIndex = braceIndex;
        } else if (semicolonIndex != -1) {
            endIndex = semicolonIndex;
        }

        return line.substring(0, endIndex).trim() + ";";
    }

    /**
     * 统计字符串中指定字符的数量。
     */
    private static int countChar(String str, char c) {
        if (str == null) return 0;
        return (int) str.chars().filter(ch -> ch == c).count();
    }

    /**
     * 使用 ASM 过滤字节码，仅保留指定方法和构造器。
     *
     * @param bytecode    原始字节码
     * @param methodNames 要保留的方法名集合
     * @return 过滤后的新字节码
     * @throws IOException 字节码处理失败
     */
    private static byte[] filterMethods(byte[] bytecode, Set<String> methodNames) throws IOException {
        ClassReader reader = new ClassReader(bytecode);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        List<MethodNode> filteredMethods = new ArrayList<>();

        // 保留所有构造器
        filteredMethods.addAll(classNode.methods.stream()
                .filter(m -> "<init>".equals(m.name))
                .collect(Collectors.toList()));

        // 保留指定方法
        filteredMethods.addAll(classNode.methods.stream()
                .filter(m -> methodNames.contains(m.name))
                .collect(Collectors.toList()));

        classNode.methods = filteredMethods;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    /**
     * 将 InputStream 转换为字节数组。
     */
    private static byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    // ========== 字节码获取 ==========

    /**
     * 获取普通类的字节码。
     */
    private static byte[] getBytecode(Class<?> targetClass) {
        return ByteCodeUtil.getClassBytesSafe(targetClass);
    }

    /**
     * 获取对象实例的字节码（支持代理对象）。
     *
     * @param bean    对象实例
     * @param isProxy 是否为代理对象
     * @return 字节码数组
     */
    private static byte[] getBytecode(Object bean, boolean isProxy) {
        if (!isProxy) {
            return ByteCodeUtil.getClassBytesSafe(bean.getClass());
        }

        Class<?> clazz = bean.getClass();
        if (ProxyUtil.isCglibProxy(bean) ||
                ProxyUtil.isSpringConfigurationCglibProxy(bean) ||
                ProxyUtil.isMyBatisMapperProxy(bean) ||
                ProxyUtil.isMockitoMock(bean) ||
                ProxyUtil.isSpringAopProxy(bean) ||
                ProxyUtil.isJdkProxyClass(bean)) {
            return ByteCodeUtil.getClassBytecodeViaInstrumentation(clazz);
        }

        return new byte[0];
    }

    // ========== 测试入口 ==========

    /**
     * 测试入口：反编译指定类并输出。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        String result = decompileClass(ArgusUserProvider.class.getName(), true);
        System.out.println(result);
    }
}