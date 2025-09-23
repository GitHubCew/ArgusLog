package githubcew.arguslog.common.util;

import java.util.regex.Pattern;

/**
 * 正则工具类
 *
 * @author chenenwei
 */
public class PatternUtil {

    // ==================== 通用工具方法 ====================

    /**
     * 检查URI是否匹配模式
     *
     * @param value     值
     * @param pattern 正则表达式模式
     * @return 如果匹配返回true，否则返回false
     */
    public static boolean match(String value, String pattern) {
        if (!pattern.contains("*")) {
            return value.equalsIgnoreCase(pattern);
        }
        return matchPattern(value.toLowerCase(), pattern.toLowerCase());
    }

    /**
     * 使用正则模式匹配URI
     *
     * @param value     值
     * @param pattern 正则表达式模式
     * @return 如果匹配返回true，否则返回false
     */
    public static boolean matchPattern(String value, String pattern) {
        String regex = "^" + escapeAndReplaceWildcard(pattern) + "$";
        return Pattern.matches(regex, value);
    }

    /**
     * 转义和替换通配符
     *
     * @param pattern 原始模式字符串
     * @return 处理后的正则表达式字符串
     */
    private static String escapeAndReplaceWildcard(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                sb.append(".*");
            } else if ("\\[]^$.{}?+|()".indexOf(c) != -1) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
