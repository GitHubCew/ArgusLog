package githubcew.arguslog.business.cmd;

import githubcew.arguslog.business.auth.ArgusUser;
import githubcew.arguslog.core.Cache;
import githubcew.arguslog.core.Constant;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 列出接口命令
 * @author  chenenwei
 */
public class Ls extends BaseCmd {

    /**
     * 构造函数
     * @param args 参数
     */
    public Ls(String cmd, String[] args) {
        super(cmd, args);
    }

    /**
     * 检查参数是否正确
     * @param args 参数
     * @return 错误信息
     */
    @Override
    public String check(String[] args) {

        if (args.length != 0 && args.length != 1) {
            return error("参数错误: 格式为：ls [接口路径]");
        }
        return Constant.EMPTY;
    }

    /**
     * 执行命令
     * @param user 用户
     * @param cmd 命令
     * @param args 参数
     * @return 执行结果
     */
    @Override
    public String execute(ArgusUser user, String cmd, String[] args) {
        List<String> sorted = Cache.getUris().stream().sorted().collect(Collectors.toList());
        if (args.length == 0) {
            return String.join(Constant.LINE_SEPARATOR, sorted);
        }
        return sorted.stream().filter(key -> key.contains(args[0])).collect(Collectors.joining(Constant.LINE_SEPARATOR));
    }
}

