package githubcew.arguslog.core.cmd.monitor;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.util.List;
import java.util.Objects;

/**
 * @author Envi
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
        String output;
        if (monitor) {
            output = output(ArgusUserContext.getCurrentUsername(), path);
        }
        // 接口
        else {
            if (Objects.isNull(path)) {
                path = "*";
            }
            output = output(null, path);
        }
        picocliOutput.out(String.join(OutputWrapper.LINE_SEPARATOR, output));
        return OK_CODE;
    }

    /**
     * 输出数据
     * @param user 用户
     * @param path 路径
     */
    private String output (String user, String path) {

        List<String> dataList;
        if (Objects.isNull(user)) {
            dataList = ArgusCache.getUrisWithPattern(path);
        } else {
            dataList = ArgusCache.getUserMonitorUris(user, path);
        }
        long total = dataList.size();
        if (dataList.size() > 30) {
            dataList = dataList.subList(0, 30);
        }

        OutputWrapper outputWrapper = OutputWrapper.wrapperCopyV2(dataList, OutputWrapper.LINE_SEPARATOR);
        if (total > 0) {
            // 添加统计输出
            outputWrapper.newLine().append(" (" + total + ")").newLine();
        }
        return outputWrapper.build();
    }

}
