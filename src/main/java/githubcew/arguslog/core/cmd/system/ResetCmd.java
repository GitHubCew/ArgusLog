package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.trace.TraceEnhanceManager;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 重置命令
 * @author chenenwei
 */
@CommandLine.Command(
        name = "reset",
        description = "重置命令",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class ResetCmd extends BaseCommand {

    @Override
    protected Integer execute() throws Exception {

        reset();
        return OK_CODE;
    }

    /**
     * 重置
     */
    public void reset() {
        try {

            String token = ArgusUserContext.getCurrentUsername();
            // 移除用户monitor方法
            ArgusCache.userRemoveAllMethod(token);

            // 移除trace增强
            List<MonitorInfo> monitorInfos = Optional.ofNullable(ArgusCache.getTraceMonitorAndNoOtherByUser(token)).orElse(new ArrayList<>());
            for (MonitorInfo monitorInfo : monitorInfos) {
                ArgusMethod argusMethod = monitorInfo.getArgusMethod();
                TraceEnhanceManager.revertClassWithKey(argusMethod.getSignature());
            }

            // 移除缓存中的trace方法
            ArgusCache.userRemoveAllTraceMethod(token);

            // 移除监控sql
            ArgusCache.removeUserSqlMonitor(token);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
