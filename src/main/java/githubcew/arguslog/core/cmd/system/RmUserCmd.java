package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.core.permission.ArgusPermissionConfigure;
import githubcew.arguslog.core.permission.Role;
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
        name = "rmuser",
        description = "移除用户",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class RmUserCmd extends BaseCommand {

    @Override
    protected Integer execute() throws Exception {
        removeUser();
        return OK_CODE;
    }

    /**
     * 移除用户
     */
    private void removeUser() throws IOException {

        ArgusUser argusUser = ArgusCache.getUserByUsername(ArgusUserContext.getCurrentUsername());

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
}
