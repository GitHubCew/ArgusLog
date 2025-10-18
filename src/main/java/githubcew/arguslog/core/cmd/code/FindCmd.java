package githubcew.arguslog.core.cmd.code;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 方法查询命令：根据 type 查询类或方法。
 * <p>
 * 支持 type=class 或 type=method 查询。
 * Spring Bean 优先匹配。
 * 控制扫描数量，防止内存或输出过大导致 CLI 崩溃。
 * </p>
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "find",
        description = "查询类或方法",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class FindCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "搜索类型：class | method",
            arity = "1",
            paramLabel = "type"
    )
    private String type;

    @CommandLine.Parameters(
            index = "1",
            description = "类名（支持通配符 '*'）",
            arity = "0..1",
            paramLabel = "className"
    )
    private String className;

    @CommandLine.Parameters(
            index = "2",
            description = "方法名（仅 type=method 有效，支持通配符 '*'）",
            arity = "0..1",
            paramLabel = "methodName"
    )
    private String methodName;

    /**
     * 扫描类名数量上限，防止输出过多。
     */
    private static final int MAX_CLASS_SCAN = 50;

    @Override
    protected Integer execute() throws Exception {

        type = type.trim().toLowerCase();

        switch (type) {

            case "method":
                if (Objects.isNull(className)) {
                    throw new RuntimeException("请指定类名");
                }
                findMethods();
                break;
            case "class":
                if (Objects.isNull(className)) {
                    throw new RuntimeException("请指定类名");
                }
                findClasses(MAX_CLASS_SCAN);
                break;
            default:
                throw new RuntimeException("不支持的类型: " + type);
        }

        return OK_CODE;
    }

    /**
     * 查询类名并输出。
     * <p>
     * 输出数量超过 50 时只显示前 50 个，并提示总数。
     * </p>
     */
    private void findClasses(int maxCount) {
        try {
            List<Class<?>> classes = findClassesByPattern(className, maxCount);
            if (classes.isEmpty()) {
                throw new RuntimeException("未找到匹配类: " + className);
            } else {
                long total = classes.size();
                if (total > 50) {
                    classes = classes.subList(0, 50);
                }
                for (Class<?> clazz : classes) {
                    picocliOutput.out(OutputWrapper.wrapperCopy(clazz.getName()));
                }
                picocliOutput.out("\n(" + total + ")");
            }
        } catch (Exception e) {
            throw new RuntimeException("查询类失败: " + e.getMessage());
        }
    }

    /**
     * 查询方法并输出。
     * <p>
     * 优先匹配 Spring Bean，未匹配则扫描 classpath。
     * 输出方法名 + 签名。
     * </p>
     */
    private void findMethods() {
        try {
            List<Class<?>> matchedClasses = new ArrayList<>();

            // 1. 优先匹配 Spring Bean
            ApplicationContext ctx = ContextUtil.context();
            if (ctx != null) {
                for (String beanName : ctx.getBeanDefinitionNames()) {
                    try {
                        Class<?> clazz = AopProxyUtils.ultimateTargetClass(ctx.getBean(beanName));
                        if (clazz != null && wildcardMatch(clazz.getName(), className)) {
                            matchedClasses.add(clazz);
                            if (matchedClasses.size() >= MAX_CLASS_SCAN) break;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // 2. classpath 扫描（非 Spring Bean）
            if (matchedClasses.isEmpty()) {
                matchedClasses.addAll(findClassesByPattern(className, MAX_CLASS_SCAN));
            }

            if (matchedClasses.isEmpty()) {
                throw new IllegalArgumentException("未找到匹配类: " + className);
            }

            Map<String, String> result = new LinkedHashMap<>();
            for (Class<?> clazz : matchedClasses) {
                collectMethods(clazz, result);
            }

            if (result.isEmpty()) {
                throw new IllegalArgumentException("未找到匹配方法");
            } else {
                result.forEach((k, v) -> picocliOutput.out(OutputWrapper.wrapperCopy(k) + " => " + v));
                picocliOutput.out("\n(" + result.size() + ")");
            }

        } catch (Exception e) {
            throw new RuntimeException("查询方法失败: " + e.getMessage());
        }
    }

    /**
     * 收集指定类中符合条件的方法到结果映射中。
     * <p>
     * 方法名支持通配符 '*'，例如 "get*" 会匹配所有以 get 开头的方法。
     * </p>
     *
     * @param clazz  目标类
     * @param result 结果容器，key: 方法签名，value: 方法名称
     */
    private void collectMethods(Class<?> clazz, Map<String, String> result) {
        for (Method method : clazz.getMethods()) { // 使用 getMethods 支持继承
            if (method.getDeclaringClass() == Object.class) continue;

            // 方法名通配符匹配（忽略大小写）
            if (methodName != null && !methodName.isEmpty()) {
                if (!wildcardMatch(method.getName(), methodName)) {
                    continue;
                }
            }

            String value = method.getName() + (Modifier.isStatic(method.getModifiers()) ? "[static]" : "");
            StringBuilder signature = new StringBuilder(clazz.getName() + "." + method.getName() + "(");
            Class<?>[] pts = method.getParameterTypes();
            for (int i = 0; i < pts.length; i++) {
                if (i > 0) signature.append(",");
                signature.append(pts[i].getName());
            }
            signature.append(")");
            result.put(signature.toString(), value);
        }
    }

    /**
     * 通配符匹配（忽略大小写）。
     * 支持 '*' 通配任意字符序列。
     *
     * @param text    文本（如类名或方法名）
     * @param pattern 模式（如 "*Controller"）
     * @return 是否匹配
     */
    private boolean wildcardMatch(String text, String pattern) {
        if (text == null || pattern == null) return false;
        text = text.toLowerCase();
        pattern = pattern.toLowerCase();

        if (pattern.equals("*")) return true;
        String[] parts = pattern.split("\\*", -1);
        int index = 0;
        boolean first = true;
        for (String part : parts) {
            if (part.isEmpty()) {
                first = false;
                continue;
            }
            int found = text.indexOf(part, index);
            if (found < 0) return false;
            if (first && !text.startsWith(part)) return false;
            index = found + part.length();
            first = false;
        }
        return pattern.endsWith("*") || index == text.length();
    }

    /**
     * 根据类名模式（支持通配符 *，忽略大小写）查找匹配的类。
     * <p>
     * 最多返回 maxCount 个匹配类。
     * </p>
     *
     * @param pattern  类名模式，如 "*Controller"
     * @param maxCount 最大返回类数量
     * @return 匹配的类列表
     */
    private List<Class<?>> findClassesByPattern(String pattern, int maxCount) {
        List<Class<?>> matched = new ArrayList<>();
        if (pattern == null || pattern.isEmpty()) return matched;

        // 没通配符，尝试直接加载
        if (!pattern.contains("*")) {
            try {
                matched.add(Class.forName(pattern));
                return matched;
            } catch (Throwable ignored) {}
        }

        String lowerPattern = pattern.toLowerCase();

        for (String name : scanAllClassNames()) {
            if (matched.size() >= maxCount) break;
            if (wildcardMatch(name.toLowerCase(), lowerPattern)) {
                try {
                    matched.add(Class.forName(name));
                } catch (Throwable ignored) {}
            }
        }

        return matched;
    }

    /**
     * 扫描 classpath 下所有可访问类名。
     * <p>
     * 兼容本地目录与 fat-jar 运行模式。
     * </p>
     *
     * @return 类全限定名集合
     */
    private Set<String> scanAllClassNames() {
        Set<String> classNames = new LinkedHashSet<>();
        String javaClassPath = System.getProperty("java.class.path");
        if (javaClassPath == null || javaClassPath.isEmpty()) return classNames;

        String[] paths = javaClassPath.split(File.pathSeparator);
        for (String path : paths) {
            File file = new File(path);
            if (file.isDirectory()) {
                scanDirectory(file, file, classNames);
            } else if (file.isFile() && file.getName().endsWith(".jar")) {
                scanJarFile(file, classNames);
            }
            if (classNames.size() > 50000) break; // 防止卡死
        }
        return classNames;
    }

    private void scanDirectory(File root, File current, Set<String> classNames) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(root, f, classNames);
            } else if (f.getName().endsWith(".class")) {
                String relative = root.toURI().relativize(f.toURI()).getPath();
                classNames.add(relative
                        .substring(0, relative.length() - 6)
                        .replace('/', '.')
                        .replace('\\', '.'));
            }
        }
    }

    private void scanJarFile(File jarFile, Set<String> classNames) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (!e.isDirectory() && e.getName().endsWith(".class")) {
                    classNames.add(e.getName()
                            .replace('/', '.')
                            .replace('\\', '.')
                            .replaceAll("\\.class$", ""));
                }
            }
        } catch (Exception ignored) {}
    }
}
