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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 命令行命令：动态调用指定类的方法。
 *
 * <p>功能说明：
 * <ul>
 *   <li>支持从 Spring 上下文中获取 Bean（优先），或通过反射创建新实例。</li>
 *   <li>支持基本类型、包装类型及 JSON 格式的复杂对象参数。</li>
 *   <li>支持混合参数，JSON 参数可包含空格或嵌套结构。</li>
 *   <li>输出结果可选择原对象或 JSON 字符串格式。</li>
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
        version = "1.0"
)
public class InvokeCmd extends BaseCommand {

    /** 方法签名，例如：cn.demo.Test.demo(java.lang.String.class,cn.cew.Demo.class) */
    @CommandLine.Parameters(
            index = "0",
            description = "方法签名，例如: cn.demo.Test.demo(java.lang.String.class,cn.cew.Demo.class)",
            arity ="1",
            paramLabel = "methodSignature"
    )
    private String methodSignature;

    /** 方法参数列表，例如：haha {"a": "1", "b": 2} true */
    @CommandLine.Parameters(
            index = "1",
            description = "参数列表，例如：haha {\"a\": \"1\", \"b\": 2} true",
            arity = "0..*",
            paramLabel = "params"
    )
    private List<String> params;

    /** 输出格式，支持 object 或 json，默认 object */
    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "输出结果格式: object | json",
            defaultValue = "object",
            arity = "0..1",
            paramLabel = "output"
    )
    private String output;

    /** JSON 转换工具 */
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 执行命令：解析方法签名和参数，调用方法并输出结果。
     *
     * <p>执行流程：
     * <ol>
     *   <li>解析方法签名，获取类名、方法名及参数类型。</li>
     *   <li>尝试从 Spring 上下文获取 Bean，若无则通过反射创建实例。</li>
     *   <li>解析命令行传入的参数，支持基本类型、包装类型及 JSON 对象类型。</li>
     *   <li>获取方法并设置可访问性，调用方法。</li>
     *   <li>输出调用结果，根据 {@code output} 参数决定输出格式。</li>
     * </ol>
     *
     * @return {@link #OK_CODE} 表示成功，{@link #ERROR_CODE} 表示调用异常
     * @throws Exception 当方法签名解析失败或反射调用失败时抛出
     */
    @Override
    protected Integer execute() throws Exception {
        // 1. 解析方法签名
        int idx = methodSignature.indexOf('(');
        if (idx < 0) {
            throw new IllegalArgumentException("方法签名格式错误，缺少 '('： " + methodSignature);
        }

        String methodFull = methodSignature.substring(0, idx).trim();
        int lastDot = methodFull.lastIndexOf('.');
        if (lastDot < 0) {
            throw new IllegalArgumentException("方法签名必须包含类名和方法名，格式：ClassName.methodName(...)，当前输入：" + methodSignature);
        }

        String className = methodFull.substring(0, lastDot).trim();
        String methodName = methodFull.substring(lastDot + 1).trim();

        String paramTypesStr = methodSignature.substring(idx + 1, methodSignature.lastIndexOf(')')).trim();
        List<String> paramTypeNames = new ArrayList<>();
        if (!paramTypesStr.isEmpty()) {
            for (String s : paramTypesStr.split(",")) {
                paramTypeNames.add(s.trim().replace(".class", ""));
            }
        }

        // 2. 获取目标类与实例
        Class<?> clazz = Class.forName(className);
        Object target = null;
        ApplicationContext ctx = ContextUtil.context();
        if (ctx != null) {
            String[] beans = ctx.getBeanNamesForType(clazz);
            if (beans.length > 0) {
                target = ctx.getBean(beans[0]);
                clazz = AopProxyUtils.ultimateTargetClass(target);
            }
        }

        // 3. 解析参数类型
        Class<?>[] paramTypes = new Class[paramTypeNames.size()];
        for (int i = 0; i < paramTypeNames.size(); i++) {
            paramTypes[i] = parseClass(paramTypeNames.get(i));
        }

        // 4. 解析参数值（支持混合类型）
        Object[] args = new Object[paramTypes.length];
        int tokenIndex = 0; // 命令行参数索引

        if (Objects.isNull(params)) {
            args = new Object[0];
        }
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> type = paramTypes[i];

            if (isPrimitiveOrString(type)) {
                if (tokenIndex >= params.size()) {
                    throw new IllegalArgumentException("参数数量不足，缺少第 " + (i + 1) + " 个参数");
                }
                args[i] = parseParamValue(type, params.get(tokenIndex));
                tokenIndex++;
            } else {
                if (tokenIndex >= params.size()) {
                    throw new IllegalArgumentException("参数数量不足，缺少第 " + (i + 1) + " 个参数");
                }
                StringBuilder sb = new StringBuilder();
                while (true) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(params.get(tokenIndex));
                    tokenIndex++;
                    try {
                        args[i] = mapper.readValue(sb.toString(), type);
                        break;
                    } catch (Exception e) {
                        if (tokenIndex >= params.size()) {
                            throw new IllegalArgumentException("无法解析 JSON 参数: " + sb.toString(), e);
                        }
                    }
                }
            }
        }

        // 5. 获取方法并调用
        Method method = findMethod(clazz, methodName, paramTypes);
        if (method == null) {
            throw new IllegalArgumentException("找不到方法：" + methodSignature);
        }

        Object result;
        try {
            if (Modifier.isStatic(method.getModifiers())) {
                result = method.invoke(null, args);
            } else {
                Object instance = target != null ? target : clazz.getDeclaredConstructor().newInstance();
                result = method.invoke(instance, args);
            }
        } catch (Exception e) {
            if (Objects.isNull(e.getMessage())) {
                throw new RuntimeException(CommonUtil.extractException(e));
            } else {
                throw new RuntimeException(e.getMessage());
            }
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
        return OK_CODE;
    }

    /**
     * 获取指定方法（支持继承和非 public 方法）。
     *
     * @param clazz      目标类
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @return Method 对象
     * @throws NoSuchMethodException 方法不存在
     */
    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, paramTypes);
                if (!Modifier.isPublic(method.getModifiers())) {
                    method.setAccessible(true);
                }
                return method;
            } catch (NoSuchMethodException ignored) {
                // 如果当前类没找到，继续查找父类
                current = current.getSuperclass();
            }
        }
        try {
            // 如果没有找到声明方法，再尝试 public 方法（包括接口）
            return clazz.getMethod(methodName, paramTypes);
        }
        catch (NoSuchMethodException e) {
            return null;
        }
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
            case "int":     return int.class;
            case "long":    return long.class;
            case "boolean": return boolean.class;
            case "double":  return double.class;
            case "float":   return float.class;
            case "short":   return short.class;
            case "byte":    return byte.class;
            case "char":    return char.class;
            case "String":  return String.class;
            default:        return Class.forName(name);
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
        if (type == char.class || type == Character.class) {
            if (value.isEmpty()) throw new IllegalArgumentException("char 类型参数不能为空");
            return value.charAt(0);
        }
        return mapper.readValue(value, type);
    }
}
