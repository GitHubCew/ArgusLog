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
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;

/**
 * 方法查询命令：根据 type 查询类或方法。
 * <p>
 * 支持 type=class 或 type=method 查询。
 * Spring Bean 优先匹配，未匹配时会扫描 classpath。
 * 支持类名和方法名通配符匹配，同时限制扫描数量防止 CLI 崩溃。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 *  查询类：find class com.demo.*Service
 *  查询方法：find method com.demo.UserService save*
 * </pre>
 *
 * @author chenenwei
 * @version 1.0
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

    /**
     * 命令执行入口
     * <p>
     * 根据 type 判断是查询类还是方法，并调用相应方法。
     * </p>
     *
     * @return 命令执行状态码，{@link #OK_CODE} 表示成功
     * @throws Exception 查询或扫描过程中可能抛出的异常
     */
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
     * 查询类名并输出
     * <p>
     * 支持通配符匹配，如果匹配数量超过 maxCount，则只显示前 maxCount 个，并提示总数。
     * </p>
     *
     * @param maxCount 最大输出数量
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
     * 查询方法并输出
     * <p>
     * 优先匹配 Spring Bean，未匹配则扫描 classpath。
     * 输出方法签名和修饰符信息。
     * 支持方法名模糊匹配（自动补全通配符 '*'）。
     * </p>
     */
    private void findMethods() {
        try {
            List<Class<?>> matchedClasses = new ArrayList<>();

            // 优先匹配 Spring Bean
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

            // classpath 扫描（非 Spring Bean）
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
     * 收集指定类中符合条件的方法
     *
     * @param clazz  要扫描的类
     * @param result 方法结果映射，key=方法签名，value=方法修饰符信息
     */
    private void collectMethods(Class<?> clazz, Map<String, String> result) {
        // 本类声明的所有方法
        for (Method method : clazz.getDeclaredMethods()) {
            addIfMatch(clazz, method, result);
        }

        // 父类的 public 方法
        for (Method method : clazz.getMethods()) {
            if (method.getDeclaringClass() != clazz && method.getDeclaringClass() != Object.class) {
                addIfMatch(clazz, method, result);
            }
        }
    }

    /**
     * 如果方法匹配条件，则加入结果
     *
     * @param clazz  方法所在类
     * @param method 要匹配的方法
     * @param result 方法结果映射
     */
    private void addIfMatch(Class<?> clazz, Method method, Map<String, String> result) {
        if (methodName != null && !methodName.isEmpty()) {
            String pattern = methodName;
            // 自动补全通配符，支持模糊匹配
            if (!pattern.contains("*")) {
                pattern = "*" + pattern + "*";
            }
            if (!wildcardMatch(method.getName(), pattern)) {
                return;
            }
        }

        String value = method.getName()
                + (Modifier.isStatic(method.getModifiers()) ? "[static]" : "")
                + (Modifier.isPrivate(method.getModifiers()) ? "[private]" : "")
                + (Modifier.isProtected(method.getModifiers()) ? "[protected]" : "");

        StringBuilder signature = new StringBuilder(clazz.getName())
                .append(".")
                .append(method.getName())
                .append("(");

        Class<?>[] pts = method.getParameterTypes();
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) signature.append(",");
            signature.append(pts[i].getName());
        }
        signature.append(")");
        result.put(signature.toString(), value);
    }

    /**
     * 通配符匹配（忽略大小写），支持 '*' 匹配任意字符序列
     *
     * @param text    文本
     * @param pattern 模式
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
     * 根据通配符查找匹配的类
     *
     * @param pattern  类名通配符
     * @param maxCount 最大返回数量
     * @return 匹配的类列表
     * @throws Exception 扫描异常
     */
    private List<Class<?>> findClassesByPattern(String pattern, int maxCount) throws Exception {
        List<Class<?>> matched = new ArrayList<>();
        if (pattern == null || pattern.isEmpty()) {
            return matched;
        }

        String lowerPattern = pattern.toLowerCase();

        // 优先从 Spring 容器中匹配 Bean
        try {
            ApplicationContext context = ContextUtil.context();
            if (context != null) {
                String[] beanNames = context.getBeanDefinitionNames();
                for (String beanName : beanNames) {
                    if (wildcardMatchIgnoreCase(beanName, lowerPattern)) {
                        Object bean = context.getBean(beanName);
                        matched.add(bean.getClass());
                        if (matched.size() >= maxCount) return matched;
                    } else {
                        Class<?> beanType = context.getType(beanName);
                        if (beanType != null && wildcardMatchIgnoreCase(beanType.getName(), lowerPattern)) {
                            matched.add(beanType);
                            if (matched.size() >= maxCount) return matched;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 尝试精确类加载
        if (!pattern.contains("*")) {
            try {
                matched.add(Class.forName(pattern));
                return matched;
            } catch (Throwable ignored) {}
        }

        // 扫描 classpath
        for (String name : scanAllClassNames()) {
            if (wildcardMatchIgnoreCase(name, lowerPattern)) {
                try {
                    matched.add(Class.forName(name));
                    if (matched.size() >= maxCount) break;
                } catch (Throwable ignored) {}
            }
        }

        return matched;
    }

    /**
     * 模糊匹配（忽略大小写，支持 * 通配符）
     *
     * @param text    文本
     * @param pattern 模式
     * @return 是否匹配
     */
    private boolean wildcardMatchIgnoreCase(String text, String pattern) {
        text = text.toLowerCase();
        pattern = pattern.toLowerCase();

        String[] parts = pattern.split("\\*", -1);
        int index = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            index = text.indexOf(part, index);
            if (index == -1) return false;
            index += part.length();
        }
        return true;
    }

    /**
     * 扫描当前 ClassPath 下所有类名
     *
     * @return 所有类的全限定名列表
     */
    private List<String> scanAllClassNames() {
        List<String> classNames = new ArrayList<>();
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources("");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    Path root = Paths.get(url.toURI());
                    Files.walk(root)
                            .filter(p -> p.toString().endsWith(".class"))
                            .forEach(p -> {
                                String name = root.relativize(p).toString()
                                        .replace(File.separatorChar, '.')
                                        .replaceAll("\\.class$", "");
                                classNames.add(name);
                            });
                } else if ("jar".equals(url.getProtocol())) {
                    String path = url.getPath();
                    String jarPath = path.substring(5, path.indexOf("!"));
                    try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, String.valueOf(StandardCharsets.UTF_8)))) {
                        jar.stream()
                                .filter(e -> e.getName().endsWith(".class"))
                                .forEach(e -> {
                                    String name = e.getName().replace('/', '.').replaceAll("\\.class$", "");
                                    classNames.add(name);
                                });
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classNames;
    }
}
