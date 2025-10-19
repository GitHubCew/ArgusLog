package githubcew.arguslog.core.cmd.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.cmd.BaseCommand;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 命令行命令：动态调用指定类的方法。
 *
 * <p>功能说明：
 * <ul>
 *   <li>支持从 Spring 上下文中获取 Bean（优先），或通过反射创建新实例。</li>
 *   <li>支持基本类型、包装类型、JSON 对象、数组、集合等复杂参数。</li>
 *   <li>自动识别 JSON 参数，非 JSON 内容按普通字符串处理。</li>
 *   <li>支持混合参数，JSON 参数可包含空格或嵌套结构。</li>
 *   <li>支持通配符匹配方法名（*、?），忽略大小写。</li>
 *   <li>兼容静态方法、继承方法、main(String[] args) 等特殊情况。</li>
 *   <li>兼容线上扫描：所有类加载、实例化、方法调用都增加容错，避免线上中断。</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 *   # 调用静态方法
 *   invoke cn.demo.Test.demo(java.lang.String.class,cn.demo.Demo.class) hello "{\"a\":1,\"b\":2}"
 *
 *   # 调用 Spring Bean 的方法
 *   invoke com.example.Service.doSomething(int.class,java.lang.String.class) 123 hello
 * </pre>
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "invoke",
        description = "动态调用指定类的方法",
        mixinStandardHelpOptions = true,
        version = "1.2"
)
public class InvokeCmd extends BaseCommand {

    /** 方法签名，例如: cn.demo.Test.demo(java.lang.String.class,int.class) */
    @CommandLine.Parameters(
            index = "0",
            description = "方法签名，例如: cn.demo.Test.demo(java.lang.String.class,int.class)",
            arity = "1",
            paramLabel = "methodSignature"
    )
    private String methodSignature;

    /** 方法参数列表，例如: haha {\"a\":1} true */
    @CommandLine.Parameters(
            index = "1",
            arity = "0..*",
            description = "方法参数: ``可用于长字符，如：json、对象参数，例如: haha `{\"a\":1}` true",
            paramLabel = "params"
    )
    private List<String> params;

    /** 输出格式，支持 object 或 json，默认 object */
    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "输出格式: object | json",
            defaultValue = "object",
            paramLabel = "output"
    )
    private String output;

    /** JSON 转换工具 */
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 执行命令：解析方法签名和参数，调用方法并输出结果。
     *
     * @return {@link #OK_CODE} 表示成功
     * @throws Exception 当解析失败或调用异常时抛出
     */
    @Override
    protected Integer execute() throws Exception {
        // 1. 解析方法签名
        int idx = methodSignature.indexOf('(');
        if (idx < 0) throw new IllegalArgumentException("方法签名格式错误：" + methodSignature);

        String methodFull = methodSignature.substring(0, idx).trim();
        int lastDot = methodFull.lastIndexOf('.');
        if (lastDot < 0) throw new IllegalArgumentException("方法签名缺少类名或方法名：" + methodSignature);

        String className = methodFull.substring(0, lastDot);
        String methodName = methodFull.substring(lastDot + 1);

        String paramTypeStr = methodSignature.substring(idx + 1, methodSignature.lastIndexOf(')')).trim();
        List<String> typeNames = new ArrayList<>();
        if (!paramTypeStr.isEmpty()) {
            for (String s : paramTypeStr.split(",")) {
                typeNames.add(s.trim().replace(".class", ""));
            }
        }

        // 2. 获取类与实例（兼容线上扫描）
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (Throwable e) {
            throw new IllegalArgumentException("无法加载类: " + className, e);
        }

        Object target = null;
        ApplicationContext ctx = ContextUtil.context();
        if (ctx != null) {
            try {
                String[] beans = ctx.getBeanNamesForType(clazz);
                if (beans.length > 0) {
                    target = ctx.getBean(beans[0]);
                    clazz = AopProxyUtils.ultimateTargetClass(target);
                }
            } catch (Throwable ignored) {
                /* Spring Bean 获取失败忽略 */
            }
        }

        // 3. 构造参数类型
        Class<?>[] paramTypes = new Class[typeNames.size()];
        for (int i = 0; i < typeNames.size(); i++) {
            paramTypes[i] = parseClass(typeNames.get(i));
        }

        // 支持反引号包裹的复杂参数（如 JSON 嵌套）
        params = mergeBacktickParams(params);
        // 4. 解析参数值（支持 JSON、混合参数）
        Object[] args = parseArguments(paramTypes, params);

        // 5. 查找方法（支持通配符匹配）并调用
        Method method = findMethodWildcard(clazz, methodName, paramTypes);
        if (method == null) throw new IllegalArgumentException("找不到方法：" + methodSignature);

        Object instance = Modifier.isStatic(method.getModifiers())
                ? null
                : safeNewInstance(clazz, target);

        Object result;
        try {
            result = method.invoke(instance, args);
        } catch (Throwable e) {
            throw new RuntimeException(CommonUtil.extractException(e));
        }

        // 6. 输出结果
        if (result != null) {
            if ("json".equalsIgnoreCase(output)) {
                try {
                    picocliOutput.out(mapper.writeValueAsString(result));
                } catch (Exception e) {
                    picocliOutput.out(result.toString());
                }
            } else {
                picocliOutput.out(result.toString());
            }
        }
        else {
            picocliOutput.out("null");
        }
        return OK_CODE;
    }

    /**
     * 将反引号包裹的参数整体合并为一个字符串。
     * 支持复杂 JSON 结构、嵌套 {}、[]、空格等。
     *
     * 示例：
     *   输入：["`{\"a\":1,", "\"b\":{\"x\":2}}`", "true"]
     *   输出：["{\"a\":1, \"b\":{\"x\":2}}", "true"]
     */
    private List<String> mergeBacktickParams(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return Collections.emptyList();

        List<String> merged = new ArrayList<>();
        StringBuilder current = null;
        boolean inBacktick = false;

        for (String token : tokens) {
            if (!inBacktick) {
                // 检测开始
                if (token.startsWith("`")) {
                    inBacktick = true;
                    current = new StringBuilder();
                    String part = token.substring(1);
                    // 单个 token 就闭合了
                    if (part.endsWith("`")) {
                        current.append(part, 0, part.length() - 1);
                        merged.add(current.toString());
                        inBacktick = false;
                    } else {
                        current.append(part);
                    }
                } else {
                    merged.add(token);
                }
            } else {
                // 处于反引号内部
                if (token.endsWith("`")) {
                    current.append(" ").append(token, 0, token.length() - 1);
                    merged.add(current.toString());
                    inBacktick = false;
                } else {
                    current.append(" ").append(token);
                }
            }
        }

        // 容错：如果反引号未闭合，直接加入剩余部分
        if (inBacktick && current != null) {
            merged.add(current.toString());
        }

        return merged;
    }

    /**
     * 安全创建实例，如果 target 不为空则直接返回，否则尝试反射实例化。
     *
     * @param clazz 目标类
     * @param target 可能已存在的实例
     * @return 实例对象，实例化失败返回 null
     */
    private Object safeNewInstance(Class<?> clazz, Object target) {
        if (target != null) return target;
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            // 容错线上环境
            return null;
        }
    }

    /**
     * 解析命令行参数到方法参数值。
     *
     * @param paramTypes 方法参数类型数组
     * @param tokens 命令行传入参数
     * @return 对应方法的参数值数组
     * @throws Exception 当解析 JSON 或基本类型失败时抛出
     */
    private Object[] parseArguments(Class<?>[] paramTypes, List<String> tokens) throws Exception {
        if (tokens == null) tokens = Collections.emptyList();
        Object[] args = new Object[paramTypes.length];
        int tokenIndex = 0;

        for (int i = 0; i < paramTypes.length; i++) {
            if (tokenIndex >= tokens.size())
                throw new IllegalArgumentException("参数不足，缺少第 " + (i + 1) + " 个参数");

            Class<?> type = paramTypes[i];
            String raw = tokens.get(tokenIndex);

            // 支持 main(String[] args)
            if (type.isArray() && type.getComponentType() == String.class && i == 0) {
                args[i] = tokens.toArray(new String[0]);
                break;
            }

            if (isPrimitiveOrString(type)) {
                args[i] = parseParamValue(type, raw);
                tokenIndex++;
                continue;
            }

            // 解析 JSON 参数
            StringBuilder sb = new StringBuilder(raw);
            while (true) {
                try {
                    args[i] = tryParseJson(sb.toString(), type);
                    tokenIndex++;
                    break;
                } catch (Exception e) {
                    tokenIndex++;
                    if (tokenIndex >= tokens.size()) {
                        args[i] = raw; // 容错回退
                        break;
                    }
                    sb.append(" ").append(tokens.get(tokenIndex));
                }
            }
        }
        return args;
    }

    /**
     * 尝试解析 JSON 字符串到指定类型，如果不是 JSON 则按基本类型解析。
     *
     * @param input JSON 字符串或普通值
     * @param type 目标类型
     * @return 解析后的对象
     * @throws Exception 当 JSON 转换失败时抛出
     */
    private Object tryParseJson(String input, Class<?> type) throws Exception {
        if (input == null) return null;
        String trimmed = input.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return mapper.readValue(trimmed, type);
        }
        return parseParamValue(type, input);
    }

    /**
     * 查找方法，支持通配符匹配方法名并忽略大小写。
     *
     * @param clazz 目标类
     * @param pattern 方法名模式
     * @param paramTypes 方法参数类型数组
     * @return 方法对象，如果未找到返回 null
     */
    private Method findMethodWildcard(Class<?> clazz, String pattern, Class<?>[] paramTypes) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            if (wildcardMatch(m.getName(), pattern)) {
                if (paramTypes.length == m.getParameterCount()) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        if (clazz.getSuperclass() != null) {
            return findMethodWildcard(clazz.getSuperclass(), pattern, paramTypes);
        }
        return null;
    }

    /**
     * 通配符匹配，支持 '*' 和 '?'，忽略大小写。
     *
     * @param text 待匹配字符串
     * @param pattern 模式
     * @return true 匹配成功
     */
    private boolean wildcardMatch(String text, String pattern) {
        text = text.toLowerCase();
        pattern = pattern.toLowerCase();

        int t = 0, p = 0, star = -1, mark = 0;
        while (t < text.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == text.charAt(t))) {
                t++; p++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                mark = t;
            } else if (star != -1) {
                p = star + 1;
                t = ++mark;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }

    /**
     * 判断类型是否为基本类型、包装类型或字符串类型。
     *
     * @param type 目标类型
     * @return true 表示是基础类型或 String，否则 false
     */
    private boolean isPrimitiveOrString(Class<?> type) {
        return type.isPrimitive() ||
                type == String.class ||
                type == Integer.class ||
                type == Long.class ||
                type == Boolean.class ||
                type == Double.class ||
                type == Float.class ||
                type == Short.class ||
                type == Byte.class ||
                type == Character.class;
    }

    /**
     * 将类型名称解析为 Class 对象，支持基本类型与普通类。
     *
     * @param name 类型名称，例如 "int"、"java.lang.String"
     * @return 对应 Class 对象
     * @throws ClassNotFoundException 当类无法找到时抛出
     */
    private Class<?> parseClass(String name) throws ClassNotFoundException {
        switch (name) {
            case "int": return int.class;
            case "long": return long.class;
            case "boolean": return boolean.class;
            case "double": return double.class;
            case "float": return float.class;
            case "short": return short.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "String": return String.class;
            default: return Class.forName(name);
        }
    }

    /**
     * 将字符串参数解析为对应类型对象。
     *
     * @param type 参数类型
     * @param value 参数字符串
     * @return 对应类型对象
     * @throws Exception 解析失败时抛出
     */
    private Object parseParamValue(Class<?> type, String value) throws Exception {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == float.class || type == Float.class) return Float.parseFloat(value);
        if (type == short.class || type == Short.class) return Short.parseShort(value);
        if (type == byte.class || type == Byte.class) return Byte.parseByte(value);
        if (type == char.class || type == Character.class) return value.charAt(0);
        return mapper.readValue(value, type);
    }
}
