package githubcew.arguslog.business.cmd;

import githubcew.arguslog.core.Cache;
import githubcew.arguslog.core.Constant;

/**
 * 移除命令
 * @author  chenenwei
 */
public class Remove extends CommonCmd{

    /**
     * 构造函数
     * @param args 参数
     */
    public Remove(String args) {
        super(args);
    }

    /**
     * 检查参数是否合法
     * @param args 参数
     * @return 错误信息
     */
    @Override
    public String check(String[] args) {
        if (args.length != 1) {
            return error("单次只能移除一个接口");
        }
        boolean has = Cache.hasUri(args[0]);
        if (!has) {
            return error("接口不存在");
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
        Cache.removeMethod(user, Cache.getMethod(args[0]));
        return Constant.OK;
    }
}
