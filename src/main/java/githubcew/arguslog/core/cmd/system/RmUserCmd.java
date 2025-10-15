package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Objects;

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

    @CommandLine.Parameters(
            index = "0",
            description = "用户名",
            arity = "0..1",
            paramLabel = "username"
    )
    private String username;

    @Override
    protected Integer execute() throws Exception {
        removeUser();
        return OK_CODE;
    }

    /**
     * 移除用户
     */
    private void removeUser() throws IOException {
        ArgusUser argusUser;
        String userToken;
        if (Objects.isNull(username)) {
            argusUser = ArgusUserContext.getCurrentUser();
        }
        else {
            checkPermission();
            argusUser = ArgusCache.getUserByUsername(username);
        }
        if (Objects.isNull(argusUser)) {
            return;
        }
        userToken = argusUser.getToken().getToken();

        // 关闭session
        if (argusUser.getSession().isOpen()) {
            argusUser.getSession().close();
        }

        // 清除用户缓存
        ArgusCache.clearUserToken(userToken);
    }

    /**
     * 校验权限
     */
    private void checkPermission() {
        // 校验是否是管理员或者是否是当前用户
        ArgusUser currentUser = ArgusUserContext.getCurrentUser();
        String currentUsername = currentUser.getAccount().getUsername();
        ArgusProperties argusProperties = ContextUtil.getBean(ArgusProperties.class);
        if (!currentUsername.equals(this.username) && !currentUsername.equals(argusProperties.getUsername())) {
            throw new RuntimeException("权限不足");
        }
    }
}
