package githubcew.arguslog.core.cmd.monitor;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.util.Objects;

/**
 * 监控命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "remove",
        description = "移除监听接口",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class RemoveCmd extends BaseCommand {

    @CommandLine.Parameters(
            description = "接口路径",
            index = "0",
            arity = "0..1",
            paramLabel = "path"

    )
    private String path;

    @CommandLine.Option(
            names = {"-a", "--all"},
            description = "移除所有接口",
            arity = "0",
            fallbackValue = "true"
    )
    private boolean all;

    /**
     * 执行逻辑
     * @return 状态码
     * @throws Exception 异常
     */
    @Override
    protected Integer execute() throws Exception {

        if (all) {
            path = "*";
        }
        else {
            if (Objects.isNull(path) || path.isEmpty()) {
                throw new RuntimeException(ERROR_PATH_EMPTY);
            }
            if (path.equals("*")) {
                throw new RuntimeException(ERROR_PATH_NOT_FOUND);
            }
        }
        ArgusCache.removeMonitorMethodWithPattern(ArgusUserContext.getCurrentUserToken(), path);
        return OK_CODE;
    }
}
