package githubcew.arguslog.core.cmd.code;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import picocli.CommandLine;
import sun.security.provider.MD5;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 方法查询命令：根据 type 查询类或方法。
 *
 * <div>
 * 支持 type=class 或 type=method 查询。
 * 可根据 proxy 参数控制扫描范围：
 * <ul>
 *     <li>proxy=true：仅扫描 Spring 容器（显示代理类）</li>
 *     <li>proxy=false：仅扫描 classpath（非代理类）</li>
 *     <li>未指定：扫描容器 + classpath</li>
 * </ul>
 * 支持类名和方法名通配符匹配，同时限制扫描数量防止 CLI 崩溃。
 * </div>
 *
 * <div>使用示例：</div>
 * <pre>
 * 查询类：find class com.demo.*Service
 * 查询方法：find method com.demo.UserService save*
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
            paramLabel = "class|method"
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

    @CommandLine.Option(
            names = {"--proxy"},
            description = "是否显示代理类",
            arity = "0..1"
    )
    private Boolean proxy;

    @CommandLine.Option(
            names = {"-e", "--exact"},
            description = "是否精确匹配类名（仅 type=class 生效，默认模糊查询）",
            arity = "0..1"
    )
    private Boolean exact;

    @CommandLine.Option(
            names = {"-p"},
            description = "扫描包名",
            arity = "1",
            paramLabel = "packageName"
    )
    private String packageName = "";

    /**
     * 扫描类名数量上限，防止输出过多
     */
    private static final int MAX_CLASS_SCAN = 50;

    /**
     * 命令执行入口
     * @return 状态码
     * @throws Exception 执行异常
     */
    @Override
    protected Integer execute() throws Exception {
        type = type.trim().toLowerCase();

        switch (type) {
            case "method":
                if (Objects.isNull(className)) throw new RuntimeException("请指定类名");
                findMethods();
                break;
            case "class":
                if (Objects.isNull(className)) throw new RuntimeException("请指定类名");
                findClasses(MAX_CLASS_SCAN);
                break;
            default:
                throw new RuntimeException("不支持的类型: " + type);
        }

        return OK_CODE;
    }

    /**
     * 查询类名并输出
     * @param maxCount 最大返回数量
     */
    private void findClasses(int maxCount) {
        try {
            List<Class<?>> classes;
            if (Boolean.TRUE.equals(exact)) {
                // 精确查询
                classes = new ArrayList<>();
                try {
                    classes.add(Class.forName(className));
                } catch (Throwable ignored) {}
            } else {
                // 模糊查询
                classes = findClassesByPattern(className, maxCount);
            }

            if (classes.isEmpty()) throw new RuntimeException("未找到匹配类: " + className);
            for (Class<?> clazz : classes) {
                picocliOutput.out(clazz.getSimpleName() + " => " + OutputWrapper.wrapperCopy(clazz.getName()));
            }
            if (classes.size() < maxCount) {
                picocliOutput.out("\n(" + classes.size() + ")");
            }
            else {
                picocliOutput.out("\n(Too many result...)");

            }
        } catch (Exception e) {
            throw new RuntimeException("查询类失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询方法并输出
     * <p>类名精确匹配，方法名支持通配符</p>
     */
    private void findMethods() {
        try {
            Class<?> clazz = null;

            // Spring 容器优先
            if (proxy == null || proxy) {
                ApplicationContext ctx = ContextUtil.context();
                if (ctx != null && ctx.containsBean(className)) {
                    Object bean = ctx.getBean(className);
                    clazz = (proxy != null && proxy) ? bean.getClass() : AopProxyUtils.ultimateTargetClass(bean);
                }
            }

            // classpath 加载
            if (clazz == null) {
                try {
                    clazz = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("未找到类: " + className);
                }
            }

            Map<String, String> result = new LinkedHashMap<>();
            collectMethods(clazz, result);

            if (result.isEmpty()) {
                throw new IllegalArgumentException("未找到匹配方法: " + methodName);
            }

            result.forEach((k, v) -> picocliOutput.out(v));
            picocliOutput.out("\n(" + result.size() + ")");
        } catch (Exception e) {
            throw new RuntimeException("查询方法失败: " + e.getMessage(), e);
        }
    }

    /**
     * 收集类中符合条件的方法
     * @param clazz 类
     * @param result 保存结果 key=方法签名，value=修饰符信息
     */
    private void collectMethods(Class<?> clazz, Map<String, String> result) {
        for (Method method : clazz.getDeclaredMethods()) addIfMatch(clazz, method, result);
        for (Method method : clazz.getMethods()) {
            if (method.getDeclaringClass() != clazz && method.getDeclaringClass() != Object.class) {
                addIfMatch(clazz, method, result);
            }
        }
    }

    /**
     * 如果方法匹配条件，则加入结果
     * @param clazz 类
     * @param method 方法
     * @param result 结果
     */
    private void addIfMatch(Class<?> clazz, Method method, Map<String, String> result) {
        if (methodName != null && !methodName.isEmpty()) {
            String pattern = methodName.contains("*") ? methodName : "*" + methodName + "*";
            if (!wildcardMatchIgnoreCase(method.getName(), pattern)) return;
        }

        String name = clazz.getSimpleName() + "." + method.getName() + "()";

        String modifiers = "";
        if (Modifier.isPublic(method.getModifiers())) {
            modifiers = "public ";
        }
        else if (Modifier.isProtected(method.getModifiers())) {
            modifiers = "protected ";
        }
        else if (Modifier.isPrivate(method.getModifiers())) {
            modifiers = "private ";
        }
        if (Modifier.isStatic(method.getModifiers())) {
            modifiers += "static ";
        }

        StringBuilder signature = new StringBuilder(clazz.getName())
                .append(".").append(method.getName()).append("(");
        Class<?>[] pts = method.getParameterTypes();
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) signature.append(",");
            signature.append(pts[i].getName());
        }
        signature.append(")");
        String returnType = method.getReturnType().getSimpleName();
        String methodInfo = modifiers + OutputWrapper.wrapperCopy(signature.toString()) + " " + returnType;

        result.put(methodInfo, name + " => " + methodInfo);
    }

    /**
     * 根据通配符查找匹配类（仅 class 查询使用）
     * @param pattern 类名通配符
     * @param maxCount 最大返回数量
     * @return 匹配类列表
     * @throws Exception 扫描异常
     */
    private List<Class<?>> findClassesByPattern(String pattern, int maxCount) throws Exception {
        if (pattern == null || pattern.isEmpty()) return Collections.emptyList();

        Set<Class<?>> matched = Collections.synchronizedSet(new LinkedHashSet<>());
        AtomicInteger count = new AtomicInteger(0);
        String lowerPattern = pattern.toLowerCase();

        boolean scanContext = proxy == null || proxy;
        boolean scanClasspath = proxy == null || !Boolean.TRUE.equals(proxy);

        // 1. Spring 容器扫描
        if (scanContext) {
            ApplicationContext ctx = ContextUtil.context();
            if (ctx != null) {
                for (String beanName : ctx.getBeanDefinitionNames()) {
                    if (count.get() >= maxCount) break;
                    try {
                        Object bean = ctx.getBean(beanName);
                        Class<?> clazz = (proxy != null && proxy) ? bean.getClass()
                                : AopProxyUtils.ultimateTargetClass(bean);

                        if (clazz == null) {
                            continue;
                        }

                        if (packageName != null && !packageName.isEmpty() && !clazz.getName().startsWith(packageName)) {
                            continue;
                        }

                        if (!wildcardMatchIgnoreCase(clazz.getName(), lowerPattern)) {
                            continue;
                        }
                        matched.add(clazz);
                        count.incrementAndGet();
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 2. 精确类
        if (!pattern.contains("*") && count.get() < maxCount) {
            try {
                matched.add(Class.forName(pattern));
                count.incrementAndGet();
            } catch (Throwable ignored) {}
        }

        // 3. 包扫描 + 模糊匹配
        if (scanClasspath && count.get() < maxCount) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            // 包路径转换
            String basePath = (packageName != null && !packageName.isEmpty())
                    ? packageName.replace('.', '/')
                    : "";

            // 构造扫描路径
            String scanPattern = "classpath*:" + (basePath.isEmpty() ? "" : basePath + "/") + "**/*.class";
            Resource[] resources = resolver.getResources(scanPattern);

            int threads = Math.min(5, resources.length);
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            for (Resource resource : resources) {
                executor.submit(() -> {
                    if (count.get() >= maxCount) return;
                    try {
                        String path = resource.getURL().getPath();
                        String className = path
                                .replaceAll(".*/classes/", "")
                                .replaceAll(".*/BOOT-INF/classes!/", "")
                                .replaceAll(".*/java/main/", "")
                                .replaceAll("/", ".")
                                .replaceAll("\\.class$", "");

                        if (path.contains("Controller")) {
                            System.out.println("类路径：" + path);
                            System.out.println("类名：" + className);
                        }

                        // 只加载指定包下的类
                        if (packageName != null && !basePath.isEmpty() && !className.startsWith(packageName)) {
                            return;
                        }

                        // 模糊匹配类名
                        if (wildcardMatchIgnoreCase(className, lowerPattern)) {
                            try {
                                matched.add(Class.forName(className));
                                count.incrementAndGet();
                            } catch (Throwable ignored) {}
                        }
                    } catch (IOException ignored) {}
                });
            }

            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        }

        return new ArrayList<>(matched).subList(0, Math.min(matched.size(), maxCount));
    }

    /**
     * 模糊匹配字符串（忽略大小写，支持 *）
     * @param text 文本
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
}