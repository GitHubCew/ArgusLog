package githubcew.arguslog.core.cmd;

import githubcew.arguslog.web.ArgusRequest;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author chenenwei
 */
@Component("argusCommandManager")
public class CommandManager {

    private final Map<String, Class<? extends BaseCommand>> commands = Collections.synchronizedMap(new LinkedHashMap<>(10));

    public void register (String command, Class<? extends BaseCommand> handler) {
        if (commands.containsKey(command)) {
            throw new RuntimeException("Command \"" + command + "\" already exists!");
        }
        commands.put(command, handler);
    }

    public void registers (Map<String, Class<? extends BaseCommand> > handlers) {
        commands.putAll(handlers);
    }


    /**
     * 移除命令
     * @param command 命令
     */
    public void unregister (String command) {
        commands.remove(command);
    }

    /**
     * 执行命令
     * @param request 请求
     * @return 执行结果
     */
        public ExecuteResult execute (ArgusRequest request) {
            RequestCommand command = request.getRequestCommand();
            Class<? extends BaseCommand> cmd = commands.get(command.getCommand());
            if (Objects.isNull(cmd)) {
                return ExecuteResult.failed("Command not found!");
            }
            try {
                BaseCommand baseCommand = cmd.newInstance();
                return ArgusCommandProcessor.execute(baseCommand, request.getRequestCommand().getArgs());
            } catch (Exception e) {
                return ExecuteResult.failed(e.getMessage());
            }
    }


    /**
     * 获取全部命令
     * @return 命令
     */
    public Map<String, Class<? extends BaseCommand>> getCommands () {
        return new LinkedHashMap<>(this.commands);
    }
}
