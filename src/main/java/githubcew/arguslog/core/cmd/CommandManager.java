package githubcew.arguslog.core.cmd;

import githubcew.arguslog.common.exception.CommandDuplicateException;
import githubcew.arguslog.web.ArgusRequest;
import org.springframework.stereotype.Component;

import java.util.*;
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
     * 不需要认证命令集
     */
    private final Set<ArgusCommand> unauthorizedCommands = Collections.synchronizedSet(new HashSet<>(10));

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
     * 注册免认证命令集
     * @param argusCommands 命令集合
     */
    public void registerUnauthorizedCommands(Collection<ArgusCommand> argusCommands) {
        unauthorizedCommands.addAll(argusCommands);
    }

    /**
     * 注册免认证命令集
     * @param argusCommand 命令
     */
    public void registerUnauthorizedCommand(ArgusCommand argusCommand) {
        unauthorizedCommands.add(argusCommand);
    }

    public boolean isUnauthorizedCommand(String command) {
        return unauthorizedCommands.stream().anyMatch(cmd -> cmd.getCmd().equals(command));
    }
    /**
     * 执行命令
     * @param request 请求
     * @return 执行结果
     */
    public ExecuteResult execute (ArgusRequest request) {
        Optional<CommandExecutor> optional = executors.values().stream().filter(e -> !Objects.isNull(e)  && e.supports(request.getRequestCommand().getCommand())).findFirst();
        CommandExecutor executor = null;
        if (optional.isPresent()) {
            executor = optional.get();
        }
        if (executor == null) {
            return ExecuteResult.failed("command not exist!");
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
