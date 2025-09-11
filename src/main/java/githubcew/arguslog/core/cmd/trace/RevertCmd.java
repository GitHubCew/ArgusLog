package githubcew.arguslog.core.cmd.trace;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.trace.TraceEnhanceManager;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.util.Objects;

/**
 * @author chenenwei
 */

@CommandLine.Command(
        name = "revert",
        description = "移除调用链监听接口",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class RevertCmd extends BaseCommand {

    @CommandLine.Parameters(
            description = "接口路径",
            arity = "0..1",
            paramLabel = "path"
    )
    private String path;

    @CommandLine.Option(
            names = {"-a"},
            description = "移除监听的全部调用链接口",
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
            revertAll();
        } else {
            revert();
        }
        return OK_CODE;
    }

    /**
     * 回退指定接口调用链
     */
    private void revert() {
        if (Objects.isNull(path)) {
            throw new RuntimeException(ERROR_PATH_EMPTY);
        }
        ArgusMethod uriMethod = ArgusCache.getUriMethod(path);
        if (uriMethod == null) {
            throw new RuntimeException(ERROR_COMMAND_NOT_FOUND);
        }

        try {
            // 回退方法调用链追踪
            String user = ArgusUserContext.getCurrentUsername();
            TraceEnhanceManager.revertClassWithKey(uriMethod.getSignature());
            // 移除监听用户
            ArgusCache.userRemoveTraceMethod(user, uriMethod);
        } catch (Exception e) {
            throw new RuntimeException("Revert error: " + e.getMessage());
        }
    }

    /**
     * 回退全部方法调用链追踪
     */
    private void revertAll() {

        TraceEnhanceManager.revertAllClasses();
        // 移除全部监听用户
        ArgusCache.userRemoveAllTraceMethod(ArgusUserContext.getCurrentUsername());
    }
}
