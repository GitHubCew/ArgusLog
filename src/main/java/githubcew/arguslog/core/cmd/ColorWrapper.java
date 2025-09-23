package githubcew.arguslog.core.cmd;

/**
 * 颜色工具
 *
 * @author chenenwei
 */

public class ColorWrapper {

    /**
     * 红色
     * @param text 文本
     * @return 颜色文本
     */
    public static String red(String text) {
        return "[#FF5F5F]" + text + "[/]";
    }

    /**
     * 绿色
     * @param text 文本
     * @return 颜色文本
     */
    public static String green(String text) {
        return "[#4CAF50]" + text + "[/]";
    }

    /**
     * 黄色
     * @param text 文本
     * @return 颜色文本
     */
    public static String yellow(String text) {
        return "[#FFCC00]" + text + "[/]";
    }

    /**
     * 蓝色
     * @param text 文本
     * @return 颜色文本
     */
    public static String blue(String text) {
        return "[#00AAFF]" + text + "[/]";
    }

    /**
     * 自定义颜色
     * @param text 文本
     * @param color 自定义颜色
     * @return 颜色文本
     */
    public static String color (String text, String color) {
        return "[#" + color + "]" + text + "[/]";
    }
}

