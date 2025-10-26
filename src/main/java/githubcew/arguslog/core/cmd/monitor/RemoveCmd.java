package githubcew.arguslog.core.cmd.monitor;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.common.util.TypeUtil;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.trace.buddy.BuddyProxyManager;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.lang.reflect.Method;
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
     *
     * @return 状态码
     * @throws Exception 异常
     */
    @Override
    protected Integer execute() throws Exception {

        boolean isMethod = !Objects.isNull(path) && path.contains(".");

        // 接口方法
        if (!isMethod) {

            if (all) {
                path = "*";
            } else {
                if (Objects.isNull(path) || path.isEmpty()) {
                    throw new RuntimeException(ERROR_PATH_EMPTY);
                }
                if (path.equals("*")) {
                    throw new RuntimeException(ERROR_PATH_NOT_FOUND);
                }
            }
            ArgusCache.removeMonitorMethodWithPattern(ArgusUserContext.getCurrentUserToken(), path);
        }
        // 普通方法
        else {

            Method method = TypeUtil.safeGetMethod(path);
            if (Objects.isNull(method)) {
                throw new RuntimeException("方法不存在");
            }
            String signature = CommonUtil.generateSignature(method);
            try {
                BuddyProxyManager.revertClassWithKey(signature);
                ArgusCache.userRemoveMethod(ArgusUserContext.getCurrentUserToken(), new ArgusMethod(method.getName(), signature, method, CommonUtil.generateCallSignature(method)));
            }
            catch (Exception e) {
                throw new RuntimeException("移除失败");
            }
        }
        return OK_CODE;
    }
}
