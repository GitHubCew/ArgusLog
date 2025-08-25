package githubcew.arguslog.common.constant;

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

    public final static String ARGUS_TERMINAL_HTML = BASE_RESOURCE_PATH + "index.html";


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

}
