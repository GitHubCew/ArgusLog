package githubcew.arguslog.core.cmd.mq;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * 消息队列命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "mq",
        description = "消息队列命令",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class MqCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "操作类型（list | monitor | remove）",
            arity = "1",
            paramLabel = "list | monitor | remove"
    )
    private String operatorType;

    @CommandLine.Parameters(
            index = "1",
            description = "队列名称",
            arity = "0..1",
            paramLabel = "queue"
    )
    private String queue;

    @Override
    protected Integer execute() throws Exception {

        switch (operatorType) {
            case "list":
                list();
                break;
            case "monitor":
                addMqMonitor();
                break;
            case "remove":
                removeMqMonitor();
                break;
            default:
                throw new RuntimeException("不支持的操作类型: " + operatorType);
        }
        return OK_CODE;
    }

    /**
     * 列出所有队列
     */
    private void list () {
        Map<String, Method> methodQueue = ArgusCache.getMethodQueue();
        OutputWrapper outputWrapper = OutputWrapper.create();
        for (Map.Entry<String, Method> entry : methodQueue.entrySet()) {
            String queue = entry.getKey();
            Method method = entry.getValue();
            outputWrapper.startCopy()
                .append(queue)
                .endCopy()
                .append("  ")
                .append(method.getDeclaringClass().getName() + "." + method.getName())
                .newLine();
        }

        picocliOutput.out(outputWrapper.build());
    }


    /**
     * 添加队列监听
     */
    private void addMqMonitor () {

        if (Objects.isNull(queue)) {
            throw new RuntimeException("请输入队列名称");
        }
        ArgusCache.addMqMonitor(ArgusUserContext.getCurrentUserToken(), queue);
    }

    /**
     * 移除监听
     */
    private void removeMqMonitor() {
        ArgusCache.removeMqMonitor(ArgusUserContext.getCurrentUserToken());
    }
}
