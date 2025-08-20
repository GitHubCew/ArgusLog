package githubcew.arguslog.core;

import java.util.regex.Pattern;

/**
 * Argus Constant
 * @author chenenwei
 */
public class ArgusConstant {

    // 成功
    public static final int SUCCESS = 1;

    // 失败
    public static final int FAILED = 0;

    public final static String BASE_RESOURCE_PATH = "META-INF/resources/argus/";

    public final static String ALOG_TERMINAL_HTML = BASE_RESOURCE_PATH + "index.html";

    public static final Pattern I18N_PATTERN = Pattern.compile("\\$\\{i18n\\.(\\w+)\\}");

    public static final String SPACE_PATTERN ="\\s+";


    /**
     * 拼接符
     */
    public final static String CONCAT_SEPARATOR = "#concat#";

    /**
     * 换行符
     */
    public final static String LINE_SEPARATOR = "\n";


    public final static String OK = "ok";
    public final static String ERROR = "error";
    public final static String EMPTY = "";

    public final static String OUT_INFO = "[@INFO@]";
    public final static String OUT_ERROR = "[@ERROR@]";

    public final static String TOKEN_SPLIT = "#token#";
}
