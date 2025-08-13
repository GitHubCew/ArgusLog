package githubcew.arguslog.business.cmd;

import githubcew.arguslog.core.Cache;
import githubcew.arguslog.core.Constant;

/**
 * 清除所有方法命令
 * @author chenenwei
 */
public class RemoveAll extends CommonCmd{

    /**
     * 构造方法
     * @param args 参数
     */
    public RemoveAll(String args) {
        super(args);
    }

    /**
     * 检查参数是否正确
     * @param args 参数
     * @return 错误信息
     */
    @Override
    public String check(String[] args) {
        if (args.length != 0) {
            return error("参数错误");
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
    public Object execute(String user, String cmd, String[] args) {
        Cache.clearUser(user);
        return Constant.OK;
    }
}
