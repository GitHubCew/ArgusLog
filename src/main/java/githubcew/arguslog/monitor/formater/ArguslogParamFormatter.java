package githubcew.arguslog.monitor.formater;

import javax.servlet.*;
import javax.servlet.http.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 默认的参数格式化器
 *
 * @author chenenwei
 */
public class ArguslogParamFormatter implements ParamFormatter {

    // 预定义常见Servlet相关类型集合（静态初始化提高性能）
    private static final Set<Class<?>> SERVLET_TYPES = new HashSet<>();

    static {
        // Servlet核心接口
        SERVLET_TYPES.add(Servlet.class);
        SERVLET_TYPES.add(ServletConfig.class);
        SERVLET_TYPES.add(ServletContext.class);
        SERVLET_TYPES.add(ServletRequest.class);
        SERVLET_TYPES.add(ServletResponse.class);

        // HTTP相关接口
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
     * 格式化
     *
     * @param parameters      参数列表
     * @param parameterValues 参数值列表
     * @return 格式化后的参数字符串
     */
    @Override
    public Object format(Parameter[] parameters, Object[] parameterValues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameters.length; i++) {
            if (isFilter(parameters[i].getType())) {
                continue;
            }
            String formatValues = formatValue(parameterValues[i]);
            sb.append("\"").append(parameters[i].getName()).append("\"").append(":").append(formatValues).append(" ");
        }
        return sb.toString();
    }

    /**
     * 格式化参数值
     *
     * @param value 参数值
     * @return 格式化后的参数值字符串
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        Class<?> clazz = value.getClass();

        if (isPrimitiveOrWrapper(clazz)) {
            if (value instanceof String && "".equals(value.toString().trim())) {
                return "\"\"";
            }
            return value.toString();
        }

        if (clazz.isArray()) {
            return formatArray(value);
        }

        if (value instanceof Collection) {
            return formatCollection((Collection<?>) value);
        }

        if (value instanceof Map) {
            return formatMap((Map<?, ?>) value);
        }

        return formatObject(value);
    }

    /**
     * 判断是否为基本类型或包装类型
     *
     * @param clazz 类
     * @return true/false
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
     * 格式化数组
     *
     * @param array 数组
     * @return 格式化后的数组字符串
     */
    private String formatArray(Object array) {
        StringBuilder sb = new StringBuilder("[");
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            sb.append(formatValue(Array.get(array, i)));
            if (i < length - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    /**
     * 格式化集合
     *
     * @param collection 集合
     * @return 格式化后的集合字符串
     */
    private String formatCollection(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        Iterator<?> it = collection.iterator();
        while (it.hasNext()) {
            sb.append(formatValue(it.next()));
            if (it.hasNext()) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    /**
     * 格式化Map
     *
     * @param map Map
     * @return 格式化后的Map字符串
     */
    private String formatMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<?, ?> entry = it.next();
            sb.append(formatValue(entry.getKey()))
                    .append("=")
                    .append(formatValue(entry.getValue()));
            if (it.hasNext()) sb.append(", ");
        }
        return sb.append("}").toString();
    }

    /**
     * 格式化普通对象（递归遍历字段）
     *
     * @param obj 对象
     * @return 格式化后的对象字符串
     */
    private String formatObject(Object obj) {
        try {
            StringBuilder sb = new StringBuilder("{");
            List<Field> fields = getAllFields(obj.getClass());
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                field.setAccessible(true);
                sb.append("\"")
                        .append(field.getName())
                        .append("\"")
                        .append(":")
                        .append(formatValue(field.get(obj)));
                if (i < fields.size() - 1) sb.append(", ");
            }
            return sb.append("}").toString();
        } catch (IllegalAccessException e) {
            return "[Error accessing fields]";
        }
    }

    /**
     * 递归获取类及其父类的所有字段
     *
     * @param clazz 类
     * @return 字段列表
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
     * 判断类是否属于Servlet相关类型（通常不可序列化）
     *
     * @param clz 要检查的类
     * @return true如果是Servlet相关类型，false否则
     */
    private boolean isFilter(Class<?> clz) {

        // 检查是否是Servlet相关类型
        for (Class<?> servletType : SERVLET_TYPES) {
            if (servletType.isAssignableFrom(clz)) {
                return true;
            }
        }

        // 额外检查常见实现类（如Spring的包装类）
        String className = clz.getName();
        return className.contains("javax.servlet") ||
                className.contains("jakarta.servlet") ||
                className.contains("org.springframework.web.context.request");
    }
}
