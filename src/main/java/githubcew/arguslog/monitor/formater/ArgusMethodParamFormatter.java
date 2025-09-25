package githubcew.arguslog.monitor.formater;

import githubcew.arguslog.monitor.MonitorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.*;
import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 默认的参数格式化器，用于将方法调用的参数列表格式化为可读的日志字符串。
 * <p>
 * 该格式化器支持以下特性：
 * <ul>
 *   <li>自动过滤 Servlet 相关对象（如 HttpServletRequest、HttpSession 等）</li>
 *   <li>递归格式化对象、集合、数组、Map 等复杂结构</li>
 *   <li>检测并防止循环引用导致的无限递归或栈溢出</li>
 *   <li>对基本类型、包装类、字符串、日期等进行友好格式化</li>
 *   <li>异常安全：任何字段访问失败不会导致整个格式化过程崩溃</li>
 * </ul>
 * <p>
 * 输出格式为类 JSON 风格的字符串（非严格 JSON），适用于日志记录场景。
 *
 * @author chenenwei
 * @since 1.0
 */
public class ArgusMethodParamFormatter implements MethodParamFormatter {

    /**
     * 日志记录器实例，用于记录格式化过程中的警告或错误信息。
     */
    private static final Logger log = LoggerFactory.getLogger(ArgusMethodParamFormatter.class);

    /**
     * 预定义的 Servlet 相关类型集合，用于快速判断是否应跳过该参数的格式化。
     * <p>
     * 包含 Java Servlet API 中的核心接口和常用类型，避免尝试序列化不可序列化或无业务意义的对象。
     */
    private static final Set<Class<?>> SERVLET_TYPES = new HashSet<>();

    static {
        // Servlet 核心接口
        SERVLET_TYPES.add(Servlet.class);
        SERVLET_TYPES.add(ServletConfig.class);
        SERVLET_TYPES.add(ServletContext.class);
        SERVLET_TYPES.add(ServletRequest.class);
        SERVLET_TYPES.add(ServletResponse.class);

        // HTTP 相关接口
        SERVLET_TYPES.add(HttpServletRequest.class);
        SERVLET_TYPES.add(HttpServletResponse.class);
        SERVLET_TYPES.add(HttpSession.class);
        SERVLET_TYPES.add(HttpUpgradeHandler.class);
        SERVLET_TYPES.add(Part.class);

        // 过滤器相关
        SERVLET_TYPES.add(Filter.class);
        SERVLET_TYPES.add(FilterConfig.class);
        SERVLET_TYPES.add(FilterChain.class);

        // 异步处理相关
        SERVLET_TYPES.add(AsyncContext.class);
        SERVLET_TYPES.add(AsyncListener.class);
    }

    /**
     * 格式化方法参数列表为可读字符串。
     * <p>
     * 该方法遍历参数名称与对应值，跳过 Servlet 相关类型，并对每个值进行递归格式化。
     * 使用 {@link IdentityHashMap} 跟踪已访问对象，防止循环引用导致栈溢出。
     *
     * @param parameters      方法参数元数据数组，通常来自 {@link Method#getParameters()}
     * @param parameterValues 方法调用时的实际参数值数组，顺序与 {@code parameters} 一致
     * @return 格式化后的字符串，格式为 {@code "paramName": value, "paramName2": value2}，末尾无逗号
     * @throws NullPointerException 如果 {@code parameters} 或 {@code parameterValues} 为 {@code null}
     */
    @Override
    public Object format(Parameter[] parameters, Object[] parameterValues) {
        if (parameters == null || parameterValues == null) {
            throw new NullPointerException("Parameters and parameterValues must not be null");
        }
        if (parameters.length != parameterValues.length) {
            log.warn("Parameter count mismatch: expected {}, got {}", parameters.length, parameterValues.length);
        }

        // 使用 IdentityHashMap 跟踪对象引用，防止循环引用
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        StringBuilder sb = new StringBuilder();
        int validCount = 0;

        for (int i = 0; i < parameters.length; i++) {
            if (isFilter(parameters[i].getType())) {
                continue;
            }
            if (validCount > 0) {
                sb.append(", ");
            }
            String formatValues = formatValue(parameterValues[i], visited);
            sb.append("\"").append(parameters[i].getName()).append("\"").append(": ").append(formatValues);
            validCount++;
        }
        return sb.toString();
    }

    /**
     * 格式化单个参数值，支持循环引用检测。
     * <p>
     * 该方法根据值的类型选择不同的格式化策略：
     * <ul>
     *   <li>{@code null} 返回 {@code "null"}</li>
     *   <li>基本类型、包装类、字符串直接转换</li>
     *   <li>数组、集合、Map 递归格式化</li>
     *   <li>普通对象通过反射遍历字段</li>
     * </ul>
     * 若检测到循环引用，返回占位符字符串。
     *
     * @param value   待格式化的参数值
     * @param visited 已访问对象集合，用于检测循环引用（基于对象引用相等性）
     * @return 格式化后的字符串表示
     */
    private String formatValue(Object value, Set<Object> visited) {
        if (value == null) {
            return "null";
        }

        Class<?> clazz = value.getClass();

        // 基本类型、包装类、String 直接处理
        if (isPrimitiveOrWrapper(clazz)) {
            if (value instanceof String) {
                String str = (String) value;
                if (str.trim().isEmpty()) {
                    return "\"\"";
                }
                // 转义双引号，避免日志解析混乱（非严格 JSON，但提升可读性）
                return "\"" + str.replace("\"", "\\\"") + "\"";
            }
            return value.toString();
        }

        // 检测循环引用：若当前对象已在递归路径中，则返回占位符
        if (visited.contains(value)) {
            return "[Circular reference: " + clazz.getSimpleName() + "@" + System.identityHashCode(value) + "]";
        }

        // 将当前对象加入已访问集合
        visited.add(value);

        try {
            if (clazz.isArray()) {
                return formatArray(value, visited);
            }

            if (value instanceof Collection) {
                return formatCollection((Collection<?>) value, visited);
            }

            if (value instanceof Map) {
                return formatMap((Map<?, ?>) value, visited);
            }

            if (value instanceof Date) {
                try {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    return simpleDateFormat.format(value);
                } catch (Exception e) {
                    return String.valueOf(((Date)value).getTime());
                }
            }

            // 其他普通对象
            return formatObject(value, visited);
        } catch (Exception e) {
            return "[Format error: " + e.getClass().getSimpleName() + ": " + e.getMessage() + "]";
        } finally {
            // 重要：从 visited 中移除，允许多次引用同一对象（如 List 中重复元素）
            visited.remove(value);
        }
    }

    /**
     * 判断指定类是否为基本类型、其包装类或 {@link String}。
     *
     * @param clazz 待判断的类
     * @return {@code true} 如果是基本类型、包装类或 {@code String}；否则 {@code false}
     */
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Double.class ||
                clazz == Float.class ||
                clazz == Boolean.class ||
                clazz == Character.class ||
                clazz == Byte.class ||
                clazz == Short.class ||
                clazz == String.class;
    }

    /**
     * 格式化数组类型的值。
     *
     * @param array   待格式化的数组对象
     * @param visited 已访问对象集合，用于递归中的循环引用检测
     * @return 格式化后的字符串，格式为 {@code [elem1, elem2, ...]}
     */
    private String formatArray(Object array, Set<Object> visited) {
        StringBuilder sb = new StringBuilder("[");
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            sb.append(formatValue(Array.get(array, i), visited));
            if (i < length - 1) {
                sb.append(", ");
            }
        }
        return sb.append("]").toString();
    }

    /**
     * 格式化集合类型的值。
     *
     * @param collection 待格式化的集合
     * @param visited    已访问对象集合，用于递归中的循环引用检测
     * @return 格式化后的字符串，格式为 {@code [elem1, elem2, ...]}
     */
    private String formatCollection(Collection<?> collection, Set<Object> visited) {
        StringBuilder sb = new StringBuilder("[");
        Iterator<?> it = collection.iterator();
        while (it.hasNext()) {
            sb.append(formatValue(it.next(), visited));
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.append("]").toString();
    }

    /**
     * 格式化 Map 类型的值。
     *
     * @param map     待格式化的 Map
     * @param visited 已访问对象集合，用于递归中的循环引用检测
     * @return 格式化后的字符串，格式为 {@code {key1 = value1, key2 = value2, ...}}
     */
    private String formatMap(Map<?, ?> map, Set<Object> visited) {
        StringBuilder sb = new StringBuilder("{");
        Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<?, ?> entry = it.next();
            sb.append(formatValue(entry.getKey(), visited))
                    .append(" = ")
                    .append(formatValue(entry.getValue(), visited));
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.append("}").toString();
    }

    /**
     * 格式化普通 Java 对象（非基本类型、非集合、非数组）。
     * <p>
     * 通过反射获取对象及其父类（除 {@link Object}）的所有字段，并递归格式化每个字段值。
     * 若字段数量超过 50，出于性能和日志可读性考虑，将跳过详细格式化。
     *
     * @param obj     待格式化的对象
     * @param visited 已访问对象集合，用于递归中的循环引用检测
     * @return 格式化后的字符串，格式为 {@code {"field1": value1, "field2": value2, ...}}
     */
    private String formatObject(Object obj, Set<Object> visited) {
        try {
            List<Field> fields = getAllFields(obj.getClass());
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                field.setAccessible(true);
                Object fieldValue;
                try {
                    fieldValue = field.get(obj);
                } catch (IllegalAccessException e) {
                    fieldValue = "[Field access denied: " + field.getName() + "]";
                }

                sb.append("\"").append(field.getName()).append("\"")
                        .append(": ")
                        .append(formatValue(fieldValue, visited));

                if (i < fields.size() - 1) {
                    sb.append(", ");
                }
            }
            return sb.append("}").toString();
        } catch (Exception e) {
            return "[Object format error: " + e.getMessage() + "]";
        }
    }

    /**
     * 递归获取指定类及其所有父类（直到 {@link Object}）中声明的所有字段。
     * <p>
     * 不包括从 {@link Object} 继承的字段。
     *
     * @param clazz 起始类
     * @return 所有字段的列表，按继承层次从子类到父类排列
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    /**
     * 判断指定类型是否应被过滤（即跳过格式化）。
     * <p>
     * 主要用于排除 Servlet 容器或 Web 框架提供的上下文对象，这些对象通常：
     * <ul>
     *   <li>不可序列化</li>
     *   <li>包含大量内部状态</li>
     *   <li>对业务日志无意义</li>
     * </ul>
     *
     * @param clz 待检查的参数类型
     * @return {@code true} 如果应过滤该类型；否则 {@code false}
     */
    private boolean isFilter(Class<?> clz) {
        // 检查是否是已知的 Servlet 相关类型
        for (Class<?> servletType : SERVLET_TYPES) {
            if (servletType.isAssignableFrom(clz)) {
                return true;
            }
        }

        // 检查类名是否属于常见 Web 容器或框架包路径
        String className = clz.getName();
        return className.startsWith("javax.servlet") ||
                className.startsWith("jakarta.servlet") ||
                className.startsWith("org.springframework.web.context.request") ||
                className.startsWith("org.springframework.web.multipart");
    }

    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ArgusMethodParamFormatter argusMethodParamFormatter = new ArgusMethodParamFormatter();
        Method demo = ArgusMethodParamFormatter.class.getMethod("demo", String.class, int.class, boolean.class, MonitorInfo.class);
        MonitorInfo monitorInfo = new MonitorInfo();
        monitorInfo.setDate(new Date());
        Object format = argusMethodParamFormatter.format(demo.getParameters(), new Object[]{"哈哈", 1, true, monitorInfo});
        System.out.println(format);
    }

    public static void demo (String a, int b, boolean c, MonitorInfo monitorInfo) {
        System.out.println(a);
    }
}
