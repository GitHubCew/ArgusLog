package githubcew.arguslog.core;

import githubcew.arguslog.core.auth.Authenticator;
import githubcew.arguslog.core.cmd.CommandManager;

import java.util.List;

/**
 * Argus配置
 * @author chenenwei
 */
public interface ArgusConfigurer {

    /**
     * 注册命令
     * @param commandManager 命令管理器
     */
    default void registerCommand (CommandManager commandManager) {
    }

    /**
     * 注册不需要认证的命令
     * @param commandManager 命令管理器
     */
    default void registerUnauthorizedCommands (CommandManager commandManager) {}

    /**
     * 注册自定义认证器
     * @param authenticators 认证器
     * @return 认证器
     */
    default List<Authenticator> registerAuthenticator (List<Authenticator> authenticators) {
        return authenticators;
    }

}
