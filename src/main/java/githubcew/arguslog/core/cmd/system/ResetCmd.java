package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.trace.TraceEnhanceManager;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 重置命令
 * @author chenenwei
 */
@CommandLine.Command(
        name = "reset",
        description = "重置清除所有监控",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class ResetCmd extends BaseCommand {

    @Override
    protected Integer execute() throws Exception {

        reset(ArgusUserContext.getCurrentUsername());
        return OK_CODE;
    }

    /**
     * 重置
     * @param username 用户名
     */
    public void reset(String username) {

        try {
            ArgusUser argusUser = ArgusCache.getUserByUsername(username);
            if (Objects.isNull(argusUser)) {
                return;
            }
            String user = argusUser.getToken().getToken();

            // 移除用户monitor方法
            ArgusCache.userRemoveAllMethod(user);

            // 移除trace增强
            List<MonitorInfo> monitorInfos = Optional.ofNullable(ArgusCache.getTraceMonitorAndNoOtherByUser(user)).orElse(new ArrayList<>());
            for (MonitorInfo monitorInfo : monitorInfos) {
                ArgusMethod argusMethod = monitorInfo.getArgusMethod();
                TraceEnhanceManager.revertClassWithKey(argusMethod.getSignature());
            }

            // 移除缓存中的trace方法
            ArgusCache.userRemoveAllTraceMethod(user);

            // 移除监控sql
            ArgusCache.removeUserSqlMonitor(user);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
