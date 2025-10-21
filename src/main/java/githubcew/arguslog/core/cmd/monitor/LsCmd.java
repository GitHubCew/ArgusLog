package githubcew.arguslog.core.cmd.monitor;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 接口列表命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "ls",
        description = "显示接口列表",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class LsCmd extends BaseCommand {

    @CommandLine.Option(
            names = {"-m"},
            description = "查看用户监听的接口,不传时查询全部接口",
            arity = "0",
            fallbackValue = "true"
    )
    private boolean monitor;

    @CommandLine.Parameters(
            description = "接口路径",
            arity = "0..1",
            paramLabel = "path"
    )
    private String path;

    /**
     * 执行逻辑
     * @return 状态码
     * @throws Exception 异常
     */
    @Override
    protected Integer execute() throws Exception {
        // 监听的接口
        if (Objects.isNull(path)) {
            path = "*";
        } else {
            if (!path.contains("*")) {
                path = "**" + path + "**";
            }
        }
        if (monitor) {
            output(ArgusUserContext.getCurrentUserToken(), path);
        }
        // 接口
        else {
            output(null, path);
        }
        return OK_CODE;
    }

    /**
     * 输出数据
     * @param user 用户
     * @param path 路径
     */
    private void output (String user, String path) {

        Map<String, Method> dataMap;
        if (Objects.isNull(user)) {
            dataMap = ArgusCache.getUrisWithPattern(path);
        } else {
            dataMap = ArgusCache.getUserMonitorUris(user, path);
        }
        long total = dataMap.size();
        if (total == 0) {
            picocliOutput.out("");
        }
        if (dataMap.size() > 20) {
            dataMap = dataMap.entrySet()
                    .stream()
                    .limit(20)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));
        }

        dataMap.forEach((uri, method) -> picocliOutput.out(OutputWrapper.wrapperCopy(uri) + "("
                + method.getDeclaringClass().getSimpleName()
                + "."
                + method.getName()
                + ")"
        ));
        if (total > 0) {
            // 添加统计输出
            picocliOutput.out(" (" + total + ") \n");
        }
    }

}
