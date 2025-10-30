package githubcew.arguslog.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <div>提供类解析、参数解析和方法查找等反射工具功能的实用类。</div>
 * <div>包含三个核心内部工具类：</div>
 * <ul>
 *   <li>{@link ClassParser}：用于将类型名称字符串解析为 {@link Class} 对象，支持基本类型、包装类型、数组、泛型简写及内部类。</li>
 *   <li>{@link ArgumentParser}：用于将字符串参数列表解析为方法调用所需的实际参数对象数组，支持基本类型、数组、JSON 对象及自动类型推断。</li>
 *   <li>{@link MethodFinder}：用于根据通配符方法名和参数类型查找匹配的方法，支持继承、接口、自动装箱及宽松类型兼容。</li>
 * </ul>
 *
 * @author chenenwei
 */
public class ClassUtil {

    /**
     * <div>类解析器，负责将类型名称字符串（如 "String"、"int[]"、"java.util.List"）转换为对应的 {@link Class} 对象。</div>
     * <div>支持基本类型、包装类型、常见集合类简写、数组（包括 JVM 内部表示法如 "[I"）、内部类（自动尝试 "$" 分隔）等。</div>
     */
    public static class ClassParser {

        private static final Map<String, String> COMMON_CLASS_MAPPING = new HashMap<>();

        static {
            // 基本类型映射
            COMMON_CLASS_MAPPING.put("String", "java.lang.String");
            COMMON_CLASS_MAPPING.put("Integer", "java.lang.Integer");
            COMMON_CLASS_MAPPING.put("Long", "java.lang.Long");
            COMMON_CLASS_MAPPING.put("Double", "java.lang.Double");
            COMMON_CLASS_MAPPING.put("Float", "java.lang.Float");
            COMMON_CLASS_MAPPING.put("Boolean", "java.lang.Boolean");
            COMMON_CLASS_MAPPING.put("List", "java.util.List");
            COMMON_CLASS_MAPPING.put("ArrayList", "java.util.ArrayList");
            COMMON_CLASS_MAPPING.put("Map", "java.util.Map");
            COMMON_CLASS_MAPPING.put("HashMap", "java.util.HashMap");
            COMMON_CLASS_MAPPING.put("Set", "java.util.Set");
            COMMON_CLASS_MAPPING.put("Date", "java.util.Date");
            COMMON_CLASS_MAPPING.put("Object", "java.lang.Object");
        }

        /**
         * <div>将给定的类型名称解析为 {@link Class} 对象。</div>
         * <div>支持以下格式：</div>
         * <ul>
         *   <li>基本类型：如 "int"、"boolean"</li>
         *   <li>包装类型简写：如 "Integer"、"String"</li>
         *   <li>数组类型：如 "int[]"、"String[]"</li>
         *   <li>JVM 内部数组表示：如 "[I"、"[Ljava.lang.String;"</li>
         *   <li>完整类名：如 "java.util.Date"</li>
         *   <li>内部类（自动尝试转换 "." 为 "$"）</li>
         * </ul>
         *
         * @param name 类型名称字符串，不可为 null 或空
         * @return 对应的 {@link Class} 对象
         * @throws ClassNotFoundException 当无法解析该类型时抛出
         * @throws IllegalArgumentException 当输入为 null 或空字符串时抛出
         */
        public static Class<?> parseClass(String name) throws ClassNotFoundException {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Class name cannot be null or empty");
            }

            // 处理基本类型
            switch (name) {
                case "int": return int.class;
                case "long": return long.class;
                case "boolean": return boolean.class;
                case "double": return double.class;
                case "float": return float.class;
                case "short": return short.class;
                case "byte": return byte.class;
                case "char": return char.class;
                case "void": return void.class;
            }

            // 处理数组类型 - 支持多种表示法
            if (name.endsWith("[]")) {
                String componentTypeName = name.substring(0, name.length() - 2);
                Class<?> componentType = parseClass(componentTypeName);
                return java.lang.reflect.Array.newInstance(componentType, 0).getClass();
            }

            // 处理 JVM 内部表示法 [Ltype;
            if (name.startsWith("[L") && name.endsWith(";")) {
                String componentTypeName = name.substring(2, name.length() - 1);
                Class<?> componentType = parseClass(componentTypeName);
                return java.lang.reflect.Array.newInstance(componentType, 0).getClass();
            }

            // 处理基本类型数组
            if (name.equals("[I")) return int[].class;
            if (name.equals("[J")) return long[].class;
            if (name.equals("[Z")) return boolean[].class;
            if (name.equals("[D")) return double[].class;
            if (name.equals("[F")) return float[].class;
            if (name.equals("[S")) return short[].class;
            if (name.equals("[B")) return byte[].class;
            if (name.equals("[C")) return char[].class;

            // 处理常见简写
            String fullClassName = COMMON_CLASS_MAPPING.get(name);
            if (fullClassName != null) {
                return Class.forName(fullClassName);
            }

            // 尝试直接加载
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                // 如果是内部类，尝试用 $ 分隔符
                if (name.contains(".") && !name.contains("$")) {
                    int lastDot = name.lastIndexOf('.');
                    String outerClass = name.substring(0, lastDot);
                    String innerClass = name.substring(lastDot + 1);
                    try {
                        return Class.forName(outerClass + "$" + innerClass);
                    } catch (ClassNotFoundException e2) {
                        // 继续抛出原始异常
                    }
                }
                throw e;
            }
        }

        /**
         * <div>将基本类型（如 {@code int.class}）包装为其对应的包装类（如 {@code Integer.class}）。</div>
         * <div>若输入非基本类型，则返回 {@code null}。</div>
         *
         * @param primitive 基本类型 {@link Class} 对象
         * @return 对应的包装类，若非基本类型则返回 {@code null}
         */
        public static Class<?> wrapPrimitive(Class<?> primitive) {
            if (primitive == int.class) return Integer.class;
            if (primitive == long.class) return Long.class;
            if (primitive == double.class) return Double.class;
            if (primitive == float.class) return Float.class;
            if (primitive == boolean.class) return Boolean.class;
            if (primitive == byte.class) return Byte.class;
            if (primitive == short.class) return Short.class;
            if (primitive == char.class) return Character.class;
            return null;
        }

        /**
         * <div>判断给定类型是否为基本类型、其包装类型或 {@link String} 类型。</div>
         * <div>还包括 {@link Class} 本身，便于参数解析场景。</div>
         *
         * @param type 要判断的类型
         * @return 若为基本类型、包装类型、String 或 Class，则返回 {@code true}；否则 {@code false}
         */
        public static boolean isPrimitiveOrString(Class<?> type) {
            return type.isPrimitive() ||
                    type == String.class ||
                    type == Integer.class ||
                    type == Long.class ||
                    type == Boolean.class ||
                    type == Double.class ||
                    type == Float.class ||
                    type == Short.class ||
                    type == Byte.class ||
                    type == Character.class ||
                    type == Class.class;
        }

        /**
         * <div>判断 {@code sourceType} 是否可赋值给 {@code targetType}，即是否满足 {@code targetType = sourceValue} 的语义。</div>
         * <div>支持以下情况：</div>
         * <ul>
         *   <li>类型完全相同</li>
         *   <li>自动装箱/拆箱（如 {@code int} 与 {@code Integer}）</li>
         *   <li>继承关系（如 {@code String} 可赋值给 {@code Object}）</li>
         *   <li>接口实现</li>
         *   <li>数组组件类型的兼容性递归检查</li>
         * </ul>
         *
         * @param targetType 目标类型
         * @param sourceType 源类型
         * @return 若可赋值则返回 {@code true}，否则 {@code false}
         */
        public static boolean isAssignable(Class<?> targetType, Class<?> sourceType) {
            // 相同类型
            if (targetType.equals(sourceType)) {
                return true;
            }

            // 自动装箱处理
            if (targetType.isPrimitive()) {
                Class<?> wrapped = wrapPrimitive(targetType);
                if (wrapped != null && wrapped.equals(sourceType)) {
                    return true;
                }
            }
            if (sourceType.isPrimitive()) {
                Class<?> wrapped = wrapPrimitive(sourceType);
                if (wrapped != null && wrapped.equals(targetType)) {
                    return true;
                }
            }

            // 继承和接口实现
            if (targetType.isAssignableFrom(sourceType)) {
                return true;
            }

            // 数组类型兼容性
            if (targetType.isArray() && sourceType.isArray()) {
                return isAssignable(targetType.getComponentType(), sourceType.getComponentType());
            }

            return false;
        }
    }

    /**
     * <div>参数解析器，负责将字符串形式的命令行参数列表转换为方法调用所需的实际参数对象数组。</div>
     * <div>支持基本类型、数组、JSON 对象、Class 类型等，并能自动拼接多 token 组成 JSON 对象。</div>
     */
    public static class ArgumentParser {

        private static final ObjectMapper mapper = new ObjectMapper();

        /**
         * <div>根据方法参数类型和输入的字符串 token 列表，解析出对应的实际参数对象数组。</div>
         * <div>支持：</div>
         * <ul>
         *   <li>基本类型及包装类型</li>
         *   <li>字符串</li>
         *   <li>Class 类型（通过 {@link ClassParser} 解析）</li>
         *   <li>任意类型的数组（通过连续 token 解析元素）</li>
         *   <li>复杂对象（尝试作为 JSON 解析，失败则回退为字符串）</li>
         * </ul>
         * <div>对于 JSON 对象，会尝试合并后续 token 直到能成功解析为止。</div>
         *
         * @param paramTypes 方法的参数类型数组
         * @param tokens 输入的字符串参数列表
         * @return 解析后的实际参数对象数组
         * @throws Exception 当参数不足、类型无法解析或 JSON 格式错误时抛出
         */
        public static Object[] parseArguments(Class<?>[] paramTypes, List<String> tokens) throws Exception {
            if (tokens == null) tokens = new ArrayList<>();
            Object[] args = new Object[paramTypes.length];
            int tokenIndex = 0;

            for (int i = 0; i < paramTypes.length; i++) {
                if (tokenIndex >= tokens.size()) {
                    throw new IllegalArgumentException("参数不足，缺少第 " + (i + 1) + " 个参数");
                }

                Class<?> type = paramTypes[i];
                String raw = tokens.get(tokenIndex);

                // 支持所有数组类型
                if (type.isArray()) {
                    Class<?> componentType = type.getComponentType();
                    List<Object> arrayElements = new ArrayList<>();

                    // 收集数组元素，直到遇到下一个参数或结束
                    while (tokenIndex < tokens.size()) {
                        String token = tokens.get(tokenIndex);

                        // 如果是下一个参数的开头（根据类型判断），停止收集
                        if (i < paramTypes.length - 1 && isNextParameter(token, paramTypes[i + 1])) {
                            break;
                        }

                        // 解析当前元素
                        Object element = parseArrayElement(componentType, token);
                        arrayElements.add(element);
                        tokenIndex++;
                    }

                    // 创建数组
                    args[i] = createArray(componentType, arrayElements);
                    continue;
                }

                // 支持 Class 类型参数
                if (type == Class.class) {
                    args[i] = ClassParser.parseClass(raw);
                    tokenIndex++;
                    continue;
                }

                if (ClassParser.isPrimitiveOrString(type)) {
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
         * <div>尝试将输入字符串解析为指定类型的对象。</div>
         * <div>若字符串符合 JSON 格式（以 {、[ 或 " 开头结尾），则使用 Jackson 进行反序列化；</div>
         * <div>否则按基本类型或字符串进行解析。</div>
         *
         * @param input 输入字符串
         * @param type 目标类型
         * @return 解析后的对象
         * @throws Exception 当解析失败时抛出
         */
        public static Object tryParseJson(String input, Class<?> type) throws Exception {
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
         * <div>将单个字符串值解析为指定类型的对象。</div>
         * <div>支持所有基本类型及其包装类、String、Character，其他类型尝试通过 Jackson 反序列化。</div>
         *
         * @param type 目标类型
         * @param value 字符串值
         * @return 解析后的对象
         * @throws Exception 当解析失败时抛出
         */
        public static Object parseParamValue(Class<?> type, String value) throws Exception {
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

        /**
         * <div>解析数组元素。</div>
         * <div>对基本类型、String、Class 等进行特殊处理，复杂类型尝试 JSON 解析。</div>
         *
         * @param componentType 数组元素类型
         * @param token 字符串 token
         * @return 解析后的元素对象
         * @throws Exception 当解析失败时抛出
         */
        private static Object parseArrayElement(Class<?> componentType, String token) throws Exception {
            if (componentType == String.class) {
                return token;
            } else if (componentType == int.class || componentType == Integer.class) {
                return Integer.parseInt(token);
            } else if (componentType == long.class || componentType == Long.class) {
                return Long.parseLong(token);
            } else if (componentType == double.class || componentType == Double.class) {
                return Double.parseDouble(token);
            } else if (componentType == float.class || componentType == Float.class) {
                return Float.parseFloat(token);
            } else if (componentType == boolean.class || componentType == Boolean.class) {
                return Boolean.parseBoolean(token);
            } else if (componentType == Class.class) {
                return ClassParser.parseClass(token);
            } else {
                // 对于复杂类型，尝试 JSON 解析
                try {
                    return tryParseJson(token, componentType);
                } catch (Exception e) {
                    // 如果 JSON 解析失败，返回原始字符串
                    return token;
                }
            }
        }

        /**
         * <div>创建指定组件类型的数组，并填充给定元素列表。</div>
         * <div>针对基本类型数组进行特殊处理以避免装箱开销，其他类型使用反射创建。</div>
         *
         * @param componentType 数组元素类型
         * @param elements 元素列表
         * @return 创建的数组对象
         */
        private static Object createArray(Class<?> componentType, List<Object> elements) {
            if (componentType == String.class) {
                return elements.toArray(new String[0]);
            } else if (componentType == int.class) {
                int[] array = new int[elements.size()];
                for (int i = 0; i < elements.size(); i++) {
                    array[i] = (Integer) elements.get(i);
                }
                return array;
            } else if (componentType == Integer.class) {
                return elements.toArray(new Integer[0]);
            } else if (componentType == long.class) {
                long[] array = new long[elements.size()];
                for (int i = 0; i < elements.size(); i++) {
                    array[i] = (Long) elements.get(i);
                }
                return array;
            } else if (componentType == Long.class) {
                return elements.toArray(new Long[0]);
            } else if (componentType == double.class) {
                double[] array = new double[elements.size()];
                for (int i = 0; i < elements.size(); i++) {
                    array[i] = (Double) elements.get(i);
                }
                return array;
            } else if (componentType == Double.class) {
                return elements.toArray(new Double[0]);
            } else if (componentType == boolean.class) {
                boolean[] array = new boolean[elements.size()];
                for (int i = 0; i < elements.size(); i++) {
                    array[i] = (Boolean) elements.get(i);
                }
                return array;
            } else if (componentType == Boolean.class) {
                return elements.toArray(new Boolean[0]);
            } else if (componentType == Class.class) {
                return elements.toArray(new Class[0]);
            } else {
                // 对于其他对象数组，使用反射创建
                Object array = Array.newInstance(componentType, elements.size());
                for (int i = 0; i < elements.size(); i++) {
                    Array.set(array, i, elements.get(i));
                }
                return array;
            }
        }

        /**
         * <div>判断当前 token 是否可能是下一个参数的开始。</div>
         * <div>仅对下一个参数为基本类型时进行简单类型试探（如能否解析为 int、long、boolean）。</div>
         * <div>用于在解析数组时决定是否停止收集元素。</div>
         *
         * @param token 当前 token 字符串
         * @param nextParamType 下一个参数的类型
         * @return 若 token 可能是下一个参数的值，则返回 {@code true}
         */
        private static boolean isNextParameter(String token, Class<?> nextParamType) {
            if (ClassParser.isPrimitiveOrString(nextParamType)) {
                try {
                    if (nextParamType == int.class || nextParamType == Integer.class) {
                        Integer.parseInt(token);
                        return true;
                    } else if (nextParamType == long.class || nextParamType == Long.class) {
                        Long.parseLong(token);
                        return true;
                    } else if (nextParamType == boolean.class || nextParamType == Boolean.class) {
                        return "true".equals(token) || "false".equals(token);
                    }
                    return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * <div>方法查找器，支持通过通配符模式匹配方法名，并根据参数类型查找匹配的方法。</div>
     * <div>查找范围包括当前类、父类及所有接口，支持精确匹配和宽松兼容匹配。</div>
     */
    public static class MethodFinder {

        /**
         * <div>在指定类及其继承体系中，查找方法名匹配通配符模式、且参数类型兼容的方法。</div>
         * <div>匹配策略：</div>
         * <ol>
         *   <li>首先尝试精确参数类型匹配</li>
         *   <li>若无，则尝试宽松兼容匹配（支持自动装箱、继承、接口）</li>
         *   <li>递归搜索父类和接口</li>
         * </ol>
         * <div>通配符支持 {@code *}（匹配任意字符）和 {@code ?}（匹配单个字符），且忽略大小写。</div>
         *
         * @param clazz 要搜索的类
         * @param pattern 方法名通配符模式（如 "get*", "set?Value"）
         * @param paramTypes 实际参数类型数组
         * @return 匹配的方法，若未找到则返回 {@code null}
         */
        public static Method findMethodWildcard(Class<?> clazz, String pattern, Class<?>[] paramTypes) {
            Method[] methods = clazz.getDeclaredMethods();

            // 首先尝试精确匹配参数类型
            for (Method m : methods) {
                if (wildcardMatch(m.getName(), pattern)) {
                    if (isParameterTypesMatch(m.getParameterTypes(), paramTypes)) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }

            // 如果没有精确匹配，尝试宽松匹配（支持自动装箱和继承）
            for (Method m : methods) {
                if (wildcardMatch(m.getName(), pattern)) {
                    if (isParameterTypesCompatible(m.getParameterTypes(), paramTypes)) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }

            // 递归查找父类
            if (clazz.getSuperclass() != null) {
                Method superMethod = findMethodWildcard(clazz.getSuperclass(), pattern, paramTypes);
                if (superMethod != null) {
                    return superMethod;
                }
            }

            // 查找接口
            for (Class<?> interfaceClass : clazz.getInterfaces()) {
                Method interfaceMethod = findMethodWildcard(interfaceClass, pattern, paramTypes);
                if (interfaceMethod != null) {
                    return interfaceMethod;
                }
            }

            return null;
        }

        /**
         * <div>精确匹配参数类型。</div>
         * <div>要求参数数量和每个参数类型完全一致。</div>
         *
         * @param methodParams 方法声明的参数类型
         * @param inputParams 实际传入的参数类型
         * @return 若完全匹配则返回 {@code true}
         */
        private static boolean isParameterTypesMatch(Class<?>[] methodParams, Class<?>[] inputParams) {
            if (methodParams.length != inputParams.length) {
                return false;
            }

            for (int i = 0; i < methodParams.length; i++) {
                if (!methodParams[i].equals(inputParams[i])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * <div>宽松匹配参数类型（支持自动装箱、继承和接口实现）。</div>
         * <div>使用 {@link ClassParser#isAssignable(Class, Class)} 进行逐个参数兼容性检查。</div>
         *
         * @param methodParams 方法声明的参数类型
         * @param inputParams 实际传入的参数类型
         * @return 若所有参数均可赋值则返回 {@code true}
         */
        private static boolean isParameterTypesCompatible(Class<?>[] methodParams, Class<?>[] inputParams) {
            if (methodParams.length != inputParams.length) {
                return false;
            }

            for (int i = 0; i < methodParams.length; i++) {
                if (!ClassParser.isAssignable(methodParams[i], inputParams[i])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * <div>使用通配符模式匹配字符串。</div>
         * <div>支持 {@code *}（匹配任意长度字符）和 {@code ?}（匹配单个字符），匹配过程忽略大小写。</div>
         *
         * @param text 被匹配的文本
         * @param pattern 通配符模式
         * @return 若匹配成功则返回 {@code true}
         */
        private static boolean wildcardMatch(String text, String pattern) {
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
         * <div>查找指定类及其继承体系中，所有方法名匹配通配符模式且参数数量匹配的方法。</div>
         * <div>用于在无精确匹配时提供候选方法列表以供提示或进一步筛选。</div>
         *
         * @param clazz 起始类
         * @param pattern 方法名通配符模式
         * @param paramCount 期望的参数数量
         * @return 所有候选方法列表
         */
        public static List<Method> findAllCandidateMethods(Class<?> clazz, String pattern, int paramCount) {
            List<Method> candidates = new ArrayList<>();
            findAllCandidateMethodsRecursive(clazz, pattern, paramCount, candidates);
            return candidates;
        }

        /**
         * <div>递归查找候选方法（包括父类和接口）。</div>
         *
         * @param clazz 当前搜索的类
         * @param pattern 方法名通配符模式
         * @param paramCount 期望的参数数量
         * @param candidates 候选方法列表（输出参数）
         */
        private static void findAllCandidateMethodsRecursive(Class<?> clazz, String pattern, int paramCount, List<Method> candidates) {
            if (clazz == null) {
                return;
            }

            // 查找当前类的方法
            for (Method method : clazz.getDeclaredMethods()) {
                if (wildcardMatch(method.getName(), pattern) &&
                        method.getParameterCount() == paramCount) {
                    candidates.add(method);
                }
            }

            // 查找父类
            findAllCandidateMethodsRecursive(clazz.getSuperclass(), pattern, paramCount, candidates);

            // 查找接口
            for (Class<?> interfaceClass : clazz.getInterfaces()) {
                findAllCandidateMethodsRecursive(interfaceClass, pattern, paramCount, candidates);
            }
        }
    }
}