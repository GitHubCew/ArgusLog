package githubcew.arguslog.business.cmd;

import githubcew.arguslog.business.auth.ArgusUser;

/**
 * 命令接口
 * @author  chenenwei
 */
public interface Cmd {

    /**
     * 检测命令是否合法
     * @param args 命令参数
     * @return 错误信息，如果命令合法，返回null
     */
    String check(String[] args);

    /**
     * 执行命令
     * @param user 用户
     * @param command 命令
     * @param args 命令参数
     * @return
     */
    Object execute (ArgusUser user, String command, String[] args);
}
