package githubcew.arguslog.core;

import java.util.regex.Pattern;

/**
 * 常量类
 * @author  chenenwei
 */
public class Constant {

    public final static String BASE_RESOURCE_PATH = "META-INF/resources/arguslog/";

    public final static String ALOG_TERMINAL_HTML = BASE_RESOURCE_PATH + "index.html";

    public final static String WS_PATH = "/arguslog-ws";

    public static final Pattern I18N_PATTERN = Pattern.compile("\\$\\{i18n\\.(\\w+)\\}");

    public static final String SPACE_PATTERN ="\\s+";


    /**
     * 拼接符
     */
    public final static String CONCAT_SEPARATOR = "@@";

    /**
     * 换行符
     */
    public final static String LINE_SEPARATOR = "\n";


    public final static  String OK = "ok";
    public final static  String ERROR = "error";
    public final static  String EMPTY = "";

    public final static  String OUT_INFO = "[@INFO@]";
    public final static  String OUT_ERROR = "[@ERROR@]";
}
