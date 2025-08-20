package githubcew.arguslog.core.cmd;

import githubcew.arguslog.core.ArgusRequest;

/**
 * @author Envi
 */
@FunctionalInterface
public interface CommandExecutor {

    /**
     * 命令执行
     * @param request 请求
     * @return 结果
     */
    ExecuteResult execute(ArgusRequest request);

    /**
     * 是否支持
     * @param command 命令
     * @return 是否支持
     */
    default boolean supports(String command) {return false;};
}
