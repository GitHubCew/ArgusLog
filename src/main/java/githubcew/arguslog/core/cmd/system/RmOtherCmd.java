package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * 移除用户命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "rmother",
        description = "移除其他用户",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class RmOtherCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "用户名",
            arity = "1",
            paramLabel = "username"
    )
    private String username;

    @CommandLine.Option(
            names = {"-a", "--all"},
            description = "移除所有用户",
            arity = "0",
            fallbackValue = "true"
    )
    private boolean all;

    @Override
    protected Integer execute() throws Exception {
        if (all) {
            removeAll();
        }
        removeUser();
        return OK_CODE;
    }

    /**
     * 移除用户
     */
    private void removeUser() throws IOException {
        ArgusUser argusUser = ArgusCache.getUserByUsername(username);
        if (Objects.isNull(argusUser)) {
            return;
        }
        String userToken = argusUser.getToken().getToken();

        if (!Objects.isNull(argusUser.getSession())) {
            // 关闭session
            if (argusUser.getSession().isOpen()) {
                argusUser.getSession().close();
            }
        }

        // 清除用户缓存
        ArgusCache.clearUserToken(userToken);
    }

    /**
     * 移除全部用户
     */
    private void removeAll() {
        // 获取在线用户
        Set<String> allOnlineUser = ArgusCache.getAllOnlineUser();
        // 移除当前用户
        allOnlineUser.removeIf(username -> !username.equals(ArgusUserContext.getCurrentUsername()));

        for (String user : allOnlineUser) {
            ArgusCache.removeUserToken(user);
        }
    }
}
