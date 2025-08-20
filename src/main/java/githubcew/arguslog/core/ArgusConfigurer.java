package githubcew.arguslog.core;

import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.core.auth.Authenticator;
import githubcew.arguslog.core.auth.TokenProvider;
import githubcew.arguslog.core.cmd.ArgusCommand;
import githubcew.arguslog.core.cmd.CommandExecutor;
import githubcew.arguslog.core.cmd.CommandManager;
import githubcew.arguslog.core.extractor.Extractor;

import java.util.List;
import java.util.Map;

/**
 * Argus配置
 * @author chenenwei
 */
public interface ArgusConfigurer {

    /**
     * 注册命令
     */
    default void registerCommand (CommandManager commandManager) {
    }

    /**
     * 注册自定义认证器
     * @param authenticators 认证器
     * @return 认证器
     */
    default List<Authenticator> registerAuthenticator (List<Authenticator> authenticators) {
        return authenticators;
    }

}
