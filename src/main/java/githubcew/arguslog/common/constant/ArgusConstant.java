package githubcew.arguslog.common.constant;

/**
 * Argus Constant
 * @author chenenwei
 */
public class ArgusConstant {

    // 成功
    public static final int SUCCESS = 1;

    // 失败
    public static final int FAILED = 0;

    public static final String COMMAND_NOT_FOUND = "命令不存在";

    public static final String PARAM_ERROR = "参数错误，请使用help查看命令的用法";

    public final static String BASE_RESOURCE_PATH = "META-INF/resources/argus/";

    public final static String ARGUS_TERMINAL_HTML = BASE_RESOURCE_PATH + "index.html";


    public static final String SPACE_PATTERN ="\\s+";

    // 拼接符
    public final static String CONCAT_SEPARATOR = "#concat#";

    // 换行符
    public final static String LINE_SEPARATOR = "\n";

    // 执行结果
    public final static String OK = "ok";

    // 复制开始
    public final static String COPY_START = "#copystart#";

    // 复制结束
    public final static String COPY_END = "#copyend#";

    public final static String OUTPUT_CONCAT = "#ouputconcat#";

}
