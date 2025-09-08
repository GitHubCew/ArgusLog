package githubcew.arguslog.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
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
}