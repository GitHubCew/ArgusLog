package githubcew.arguslog.monitor.formater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 默认的参数格式化器，用于将方法调用的参数列表格式化为可读的日志字符串。
 * <p>
 * 该格式化器支持以下特性：
 * <ul>
 *   <li>自动过滤 Servlet 相关对象（如 {@code HttpServletRequest}、{@code HttpSession} 等），避免日志泄露或序列化异常；</li>
 *   <li>递归格式化对象、集合、数组、Map 等复杂结构，输出类 JSON 风格字符串；</li>
 *   <li>检测并防止循环引用导致的无限递归或栈溢出；</li>
 *   <li>对基本类型、包装类、字符串、日期等进行友好格式化；</li>
 *   <li>异常安全：任何字段访问或反射失败不会导致整个格式化过程崩溃；</li>
 *   <li>限制递归深度、集合大小、Map 条目数、对象字段数，防止内存溢出或日志爆炸。</li>
 * </ul>
 * <p>
 * 输出格式为类 JSON 风格（非严格 JSON），适用于日志记录、监控、调试等场景。
 *
 * @author chenenwei
 * @since 1.0
 */
public class ArgusMethodParamFormatter implements MethodParamFormatter {

    private static final Logger log = LoggerFactory.getLogger(ArgusMethodParamFormatter.class);

    /**
     * 默认最大递归深度，用于防止在嵌套对象结构中发生栈溢出。
     */
    private static final int DEFAULT_MAX_DEPTH = 5;

    /**
     * 实际使用的最大递归深度，可通过 {@link #setMaxDepth(int)} 动态配置。
     */
    private int maxDepth = DEFAULT_MAX_DEPTH;

    /**
     * 预定义的 Servlet 相关类型集合，用于自动过滤敏感或无意义的 Web 层对象。
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
     * 设置最大递归深度。
     * <p>
     * 当格式化嵌套对象时，超过此深度的子对象将被截断，避免栈溢出。
     * </p>
     *
     * @param maxDepth 最大递归深度，必须大于 0
     * @throws IllegalArgumentException 如果 {@code maxDepth <= 0}
     */
    public void setMaxDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("Max depth must be greater than 0");
        }
        this.maxDepth = maxDepth;
    }

    /**
     * 获取当前配置的最大递归深度。
     *
     * @return 当前最大深度值
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * 格式化方法参数列表为可读字符串。
     * <p>
     * 该方法会：
     * <ul>
     *   <li>跳过所有 Servlet 相关参数（如 {@code HttpServletRequest}）；</li>
     *   <li>按参数名-值对格式输出；</li>
     *   <li>使用引用跟踪防止循环引用；</li>
     *   <li>对复杂对象进行安全递归格式化。</li>
     * </ul>
     *
     * @param parameters      方法参数元数据数组（来自 {@code Method.getParameters()}）
     * @param parameterValues 实际参数值数组
     * @return 格式化后的字符串，格式如：{@code "param1": "value1", "param2": { ... }}
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
            String formatValues = formatValue(parameterValues[i], visited, 0);
            sb.append("\"").append(parameters[i].getName()).append("\"").append(": ").append(formatValues);
            validCount++;
        }
        return sb.toString();
    }

    /**
     * 格式化单个参数值，支持循环引用检测和深度限制。
     *
     * @param value   待格式化的参数值
     * @param visited 已访问对象集合（基于引用相等），用于检测循环引用
     * @param depth   当前递归深度（从 0 开始）
     * @return 格式化后的字符串表示
     */
    private String formatValue(Object value, Set<Object> visited, int depth) {
        if (value == null) {
            return "null";
        }

        // 检查深度限制
        if (depth >= maxDepth) {
            return "[Max depth reached: " + value.getClass().getSimpleName() + "]";
        }

        Class<?> clazz = value.getClass();

        // 基本类型、包装类、String 直接处理
        if (isPrimitiveOrWrapper(clazz)) {
            if (value instanceof String) {
                String str = (String) value;
                if (str.trim().isEmpty()) {
                    return "\"\"";
                }
                return "\"" + str.replace("\"", "\\\"") + "\"";
            }
            return value.toString();
        }

        // 检测循环引用
        if (visited.contains(value)) {
            return "[Circular reference: " + clazz.getSimpleName() + "@" + System.identityHashCode(value) + "]";
        }

        // 将当前对象加入已访问集合
        visited.add(value);

        try {
            if (clazz.isArray()) {
                return formatArray(value, visited, depth);
            }

            if (value instanceof Collection) {
                return formatCollection((Collection<?>) value, visited, depth);
            }

            if (value instanceof Map) {
                return formatMap((Map<?, ?>) value, visited, depth);
            }

            if (value instanceof Date) {
                try {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    return "\"" + simpleDateFormat.format(value) + "\"";
                } catch (Exception e) {
                    return String.valueOf(((Date)value).getTime());
                }
            }

            // 其他普通对象
            return formatObject(value, visited, depth);
        } catch (Exception e) {
            log.warn("Format value error, class: {}, message: {}", clazz.getName(), e.getMessage());
            return "[Format error: " + e.getClass().getSimpleName() + ": " + e.getMessage() + "]";
        } finally {
            visited.remove(value);
        }
    }

    /**
     * 格式化数组类型的值。
     * <p>
     * 限制最多显示 100 个元素，超出部分以省略号提示。
     * </p>
     *
     * @param array   待格式化的数组对象
     * @param visited 已访问对象集合
     * @param depth   当前递归深度
     * @return 格式化后的数组字符串，如 {@code [1, 2, 3]}
     */
    private String formatArray(Object array, Set<Object> visited, int depth) {
        StringBuilder sb = new StringBuilder("[");
        int length = Array.getLength(array);

        // 限制数组元素数量，防止过大数组
        int displayLength = Math.min(length, 100);
        for (int i = 0; i < displayLength; i++) {
            sb.append(formatValue(Array.get(array, i), visited, depth + 1));
            if (i < displayLength - 1) {
                sb.append(", ");
            }
        }

        if (length > displayLength) {
            sb.append(", ... and ").append(length - displayLength).append(" more");
        }

        return sb.append("]").toString();
    }

    /**
     * 格式化集合类型的值。
     * <p>
     * 限制最多显示 100 个元素。
     * </p>
     *
     * @param collection 待格式化的集合
     * @param visited    已访问对象集合
     * @param depth      当前递归深度
     * @return 格式化后的集合字符串，如 {@code [a, b, c]}
     */
    private String formatCollection(Collection<?> collection, Set<Object> visited, int depth) {
        StringBuilder sb = new StringBuilder("[");
        Iterator<?> it = collection.iterator();

        // 限制集合元素数量
        int count = 0;
        int maxElements = 100;

        while (it.hasNext() && count < maxElements) {
            sb.append(formatValue(it.next(), visited, depth + 1));
            if (it.hasNext() && count < maxElements - 1) {
                sb.append(", ");
            }
            count++;
        }

        if (collection.size() > maxElements) {
            sb.append(", ... and ").append(collection.size() - maxElements).append(" more");
        }

        return sb.append("]").toString();
    }

    /**
     * 格式化 Map 类型的值。
     * <p>
     * 限制最多显示 50 个条目，键值对以 {@code key = value} 形式展示。
     * </p>
     *
     * @param map     待格式化的 Map
     * @param visited 已访问对象集合
     * @param depth   当前递归深度
     * @return 格式化后的 Map 字符串，如 {@code {name = "John", age = 30}}
     */
    private String formatMap(Map<?, ?> map, Set<Object> visited, int depth) {
        StringBuilder sb = new StringBuilder("{");
        Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();

        // 限制 Map 条目数量
        int count = 0;
        int maxEntries = 50;

        while (it.hasNext() && count < maxEntries) {
            Map.Entry<?, ?> entry = it.next();
            sb.append(formatValue(entry.getKey(), visited, depth + 1))
                    .append(" = ")
                    .append(formatValue(entry.getValue(), visited, depth + 1));
            if (it.hasNext() && count < maxEntries - 1) {
                sb.append(", ");
            }
            count++;
        }

        if (map.size() > maxEntries) {
            sb.append(", ... and ").append(map.size() - maxEntries).append(" more entries");
        }

        return sb.append("}").toString();
    }

    /**
     * 格式化普通 Java 对象（POJO）。
     * <p>
     * 递归获取所有字段（包括父类），限制最多 50 个字段，避免日志过长。
     * </p>
     *
     * @param obj     待格式化的对象
     * @param visited 已访问对象集合
     * @param depth   当前递归深度
     * @return 格式化后的对象字符串，如 {@code {"id": 1, "name": "test"}}
     */
    private String formatObject(Object obj, Set<Object> visited, int depth) {
        try {
            List<Field> fields = getAllFields(obj.getClass());

            // 限制对象字段数量
            if (fields.size() > 50) {
                return "[Too many fields: " + fields.size() + " in " + obj.getClass().getSimpleName() + "]";
            }

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
                        .append(formatValue(fieldValue, visited, depth + 1));

                if (i < fields.size() - 1) {
                    sb.append(", ");
                }
            }
            return sb.append("}").toString();
        } catch (Exception e) {
            log.warn("Format object error, class: {}, message: {}", obj.getClass().getName(), e.getMessage());
            return "[Object format error: " + e.getMessage() + "]";
        }
    }

    /**
     * 判断指定类是否为基本类型、其包装类或 {@link String}。
     *
     * @param clazz 待判断的类
     * @return 若为基本类型或常用不可变类型，返回 {@code true}
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
     * 递归获取指定类及其所有父类（直到 {@code Object}）中声明的所有字段。
     *
     * @param clazz 起始类
     * @return 所有字段的列表（不包含 {@code Object} 的字段）
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
     * 判断指定类型是否应被过滤（即不参与日志格式化）。
     * <p>
     * 过滤规则：
     * <ul>
     *   <li>属于预定义的 Servlet 接口或其实现类；</li>
     *   <li>类名以 {@code javax.servlet}、{@code jakarta.servlet}、
     *       {@code org.springframework.web.context.request} 或
     *       {@code org.springframework.web.multipart} 开头。</li>
     * </ul>
     *
     * @param clz 待判断的类型
     * @return 若应被过滤，返回 {@code true}
     */
    private boolean isFilter(Class<?> clz) {
        for (Class<?> servletType : SERVLET_TYPES) {
            if (servletType.isAssignableFrom(clz)) {
                return true;
            }
        }

        String className = clz.getName();
        return className.startsWith("javax.servlet") ||
                className.startsWith("jakarta.servlet") ||
                className.startsWith("org.springframework.web.context.request") ||
                className.startsWith("org.springframework.web.multipart");
    }
}