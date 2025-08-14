package githubcew.arguslog.business.cmd;

import githubcew.arguslog.business.auth.ArgusUser;
import githubcew.arguslog.core.Cache;
import githubcew.arguslog.core.Constant;

import java.util.stream.Collectors;

/**
 * @author chenenwei
 */
public class LsMonitor extends BaseCmd {
    /**
     * 构造函数
     * @param args 参数
     */
    public LsMonitor(String cmd, String[] args) {
        super(cmd, args);
    }

    /**
     * 检查参数是否正确
     * @param args 参数
     * @return 错误信息
     */
    @Override
    public String check(String[] args) {

        if (args.length != 0) {
            return error("参数错误: 格式为：lsm ");
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
        return Cache.getUrisByUser(user.getSessionId()).stream().sorted().collect(Collectors.joining(Constant.LINE_SEPARATOR));
    }
}
