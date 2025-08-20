package githubcew.arguslog.core.cmd;

import githubcew.arguslog.core.ArgusConstant;
import githubcew.arguslog.core.ArgusRequest;
import githubcew.arguslog.core.exception.CommandDuplicateException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author chenenwei
 */
@Component("argusCommandManager")
public class CommandManager {

    /**
     * 处理器
     */
    private final Map<ArgusCommand, CommandExecutor> executors = Collections.synchronizedMap(new LinkedHashMap<>(10));

    /**
     * 忽略认证命令
     */
    private final Set<ArgusCommand> ignoreAuthorization = Collections.synchronizedSet(new HashSet<>(10));

    /**
     * 注册命令
     * @param command 命令
     * @param executor 执行器
     */
    public void register (ArgusCommand command, CommandExecutor executor) {
        executors.put(command, executor);
    }

    /**
     * 注册命令
     * @param commandExecutorMap 命令集
     */
    public void register (Map<ArgusCommand,CommandExecutor> commandExecutorMap) {

        List<String> existCommands = commandExecutorMap.keySet().stream().map(ArgusCommand::getCmd).collect(Collectors.toList());
        Optional<ArgusCommand> any = executors.keySet().stream().filter(n -> existCommands.contains(n.getCmd())).findAny();
        if (any.isPresent()) {
            throw new CommandDuplicateException(any.get().getCmd());
        }
        executors.putAll(commandExecutorMap);
    }

    /**
     * 移除命令
     * @param command 命令
     */
    public void unregister (ArgusCommand command) {
        executors.remove(command);
    }

    /**
     * 添加忽略认证命令
     * @param argusCommand 命令
     */
    public void ignoreAuthorization (ArgusCommand argusCommand) {
        ignoreAuthorization.add(argusCommand);
    }

    /**
     * 添加忽略认证命令
     * @param argusCommands 命令
     */
    public void ignoreAuthorization (Collection<ArgusCommand> argusCommands) {
        ignoreAuthorization.addAll(argusCommands);
    }

    public boolean isIgnoreAuthorization (String command) {
        return ignoreAuthorization.stream().anyMatch(cmd -> cmd.getCmd().equals(command));
    }
    /**
     * 执行命令
     * @param request 请求
     * @return 执行结果
     */
    public ExecuteResult execute (ArgusRequest request) {
        Optional<CommandExecutor> optional = executors.values().stream().filter(e -> e.supports(request.getRequestCommand().getCommand())).findFirst();
        CommandExecutor executor = null;
        if (optional.isPresent()) {
            executor = optional.get();
        }
        if (executor == null) {
            return new ExecuteResult(ArgusConstant.FAILED, "command not exist!");
        }
        return executor.execute(request);
    }

    /**
     * 获取全部命令
     * @return 命令
     */
    public List<ArgusCommand> getCommands () {
        return new ArrayList<>(executors.keySet());
    }
}
