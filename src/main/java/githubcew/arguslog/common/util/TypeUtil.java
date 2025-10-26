package githubcew.arguslog.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeUtil {

    /**
     * 判断字段是否是指定的泛型类型
     * 例如：isParameterizedType(field, Set.class, String.class) → 判断是否为 Set
     *
     * @param field 字段
     * @param rawType 原始类型，如 Set.class、List.class、Map.class
     * @param genericTypes 泛型参数类型，如 String.class, Integer.class...
     * @return 是否匹配
     */
    public static boolean isParameterizedType(Field field, Class<?> rawType, Class<?>... genericTypes) {
        try {
            Type fieldType = field.getGenericType();

            // 必须是 ParameterizedType（带泛型）
            if (!(fieldType instanceof ParameterizedType)) {
                return false;
            }

            ParameterizedType pType = (ParameterizedType) fieldType;

            // 检查原始类型
            if (pType.getRawType() != rawType) {
                return false;
            }

            // 检查泛型参数数量
            Type[] actualArgs = pType.getActualTypeArguments();
            if (actualArgs.length != genericTypes.length) {
                return false;
            }

            // 逐个比较泛型参数
            for (int i = 0; i < genericTypes.length; i++) {
                if (!(actualArgs[i] instanceof Class)) {
                    return false; // 不支持泛型变量 T
                }
                if (!actualArgs[i].equals(genericTypes[i])) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断字段是否是某个原始类型（忽略泛型）
     * 例如：isRawType(field, Set.class) → true if Set
     * @param field 字段
     * @param rawType 类型
     * @return 是否匹配
     */
    public static boolean isRawType(Field field, Class<?> rawType) {
        try {
            Type fieldType = field.getGenericType();
            if (fieldType instanceof ParameterizedType) {
                return ((ParameterizedType) fieldType).getRawType() == rawType;
            } else if (fieldType instanceof Class) {
                return ((Class<?>) fieldType).isAssignableFrom(rawType) || rawType.isAssignableFrom((Class<?>) fieldType);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断字段是否是 Collection
     * @param field 字段
     * @return 是否匹配
     */
    public static boolean isListOfString(Field field) {
        return isParameterizedType(field, List.class, String.class);
    }

    /**
     * 判断字段是否是 Set
     * @param field 字段
     * @return 是否匹配
     */
    public static boolean isSetOfString(Field field) {
        return isParameterizedType(field, Set.class, String.class);
    }

    /**
     * 判断字段是否是 Map
     * @param field 字段
     * @return 是否匹配
     */
    public static boolean isMapOfStringString(Field field) {
        return isParameterizedType(field, Map.class, String.class, String.class);
    }

    /**
     * 判断类型是否为基本类型、包装类型或字符串类型。
     *
     * @param type 目标类型
     * @return true 表示是基础类型或 String，否则 false
     */
    private static boolean isPrimitiveOrString(Class<?> type) {
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
    private static Class<?> parseClass(String name) throws ClassNotFoundException {
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
    private static Object parseParamValue(Class<?> type, String value) throws Exception {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == float.class || type == Float.class) return Float.parseFloat(value);
        if (type == short.class || type == Short.class) return Short.parseShort(value);
        if (type == byte.class || type == Byte.class) return Byte.parseByte(value);
        if (type == char.class || type == Character.class) return value.charAt(0);
        return null;
    }

    /**
     * 安全获取方法
     * @param signature 方法签名
     * @return 方法对象
     */
    public static Method safeGetMethod (String signature) {
        try {
            // 1. 解析方法签名
            int idx = signature.indexOf('(');
            if (idx < 0) {
                throw new IllegalArgumentException("方法签名格式错误：" + signature);
            }

            String methodFull = signature.substring(0, idx).trim();
            int lastDot = methodFull.lastIndexOf('.');
            if (lastDot < 0) {
                throw new IllegalArgumentException("方法签名缺少类名或方法名：" + signature);
            }

            String className = methodFull.substring(0, lastDot);
            String methodName = methodFull.substring(lastDot + 1);

            String paramTypeStr = signature.substring(idx + 1, signature.lastIndexOf(')')).trim();
            List<String> typeNames = new ArrayList<>();
            if (!paramTypeStr.isEmpty()) {
                for (String s : paramTypeStr.split(",")) {
                    typeNames.add(s.trim().replace(".class", ""));
                }
            }

            Class<?> clazz = Class.forName(className);

            Class<?>[] paramTypes = new Class[typeNames.size()];
            for (int i = 0; i < typeNames.size(); i++) {
                paramTypes[i] = parseClass(typeNames.get(i));
            }

            return clazz.getMethod(methodName, paramTypes);

        }
        catch (Exception e) {
            return null;
        }
    }
}