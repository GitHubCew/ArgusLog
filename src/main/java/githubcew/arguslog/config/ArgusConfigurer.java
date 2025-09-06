package githubcew.arguslog.config;

import githubcew.arguslog.core.cmd.CommandManager;

/**
 * Argus自定义配置接口
 *
 * @author chenenwei
 */
public interface ArgusConfigurer {

    /**
     * 注册命令
     *
     * @param commandManager 命令管理器
     */
    default void registerCommand(CommandManager commandManager) {
    }

}
