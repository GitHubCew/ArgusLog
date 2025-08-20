package githubcew.arguslog.core.cmd;

import githubcew.arguslog.core.ArgusCache;
import githubcew.arguslog.core.ArgusConfigurer;
import githubcew.arguslog.core.ArgusConstant;
import githubcew.arguslog.core.ArgusRequest;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.method.ArgusMethod;
import githubcew.arguslog.core.method.MonitorInfo;
import org.springframework.core.Constants;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Argus 内置命令
 * @author chenenwei
 */
@Order(-Integer.MAX_VALUE)
@Component
public class ArgusCommandRegistry implements ArgusConfigurer {

    private CommandManager commandManager;

    @Override
    public void registerCommand(CommandManager commandManager) {

        this.commandManager = commandManager;

        // 添加Argus内置命令
        this.commandManager.register(help());
        this.commandManager.register(ls());
        this.commandManager.register(monitor());
    }

    /**
     * help 命令
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> help () {
        ArgusCommand command = new ArgusCommand("help", "命令用法查看", "help [command]", "help ls");
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return command.equals("help");
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();
                if (args.length > 1) {
                    return new ExecuteResult(ArgusConstant.FAILED, "param error!");
                }
                List<ArgusCommand> commands;
                if (args.length == 1) {
                    commands = commandManager.getCommands().stream().filter(c -> c.getCmd().equals(args[0])).sorted().collect(Collectors.toList());
                } else {
                    commands = commandManager.getCommands().stream().sorted().collect(Collectors.toList());
                }

                StringBuilder sb = new StringBuilder();
                sb.append(ArgusConstant.LINE_SEPARATOR);

                for (ArgusCommand cmd : commands) {
                    sb.append("【").append(cmd.getCmd()).append("】").append(ArgusConstant.LINE_SEPARATOR)
                            .append("   用法: ").append(cmd.getUsage()).append(ArgusConstant.LINE_SEPARATOR)
                            .append("   介绍: ").append(cmd.getIntroduction()).append(ArgusConstant.LINE_SEPARATOR)
                            .append("   例子: ").append(cmd.getExample()).append(ArgusConstant.LINE_SEPARATOR)
                            .append(ArgusConstant.LINE_SEPARATOR);
                }
                return new ExecuteResult(ArgusConstant.SUCCESS, sb.toString());
            }
        };

        Map<ArgusCommand, CommandExecutor> help = new HashMap<>();
        help.put(command, executor);
        return help;

    }

    /**
     * ls命令
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> ls () {
        ArgusCommand command = new ArgusCommand("ls", "列出监听的接口列表", "ls [path]", "ls /api/v1");
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return command.equals("ls");
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();
                if (args.length > 1) {
                    return new ExecuteResult(ArgusConstant.FAILED, "param error!");
                }
                String uri = "";
                if (args.length == 1) {
                    uri = args[0];
                }
                List<String> data = ArgusCache.listUriMethod(uri);
                return new ExecuteResult(ArgusConstant.SUCCESS, String.join(ArgusConstant.LINE_SEPARATOR, data));
            }
        };

        Map<ArgusCommand, CommandExecutor> ls = new HashMap<>();
        ls.put(command, executor);
        return ls;

    }

    /**
     * ls命令
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> monitor () {
        ArgusCommand command = new ArgusCommand("monitor", "监听接口,<api>为接口路径,[target]可选值为：param（参数）,result（结果）,time（耗时）,ex（异常） ", "monitor <api> [target]", "monitor /api/v1/demo param,result");
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return command.equals("monitor");
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();
                if (args.length > 2) {
                    return new ExecuteResult(ArgusConstant.FAILED, "param error!");
                }
                String uri = "";
                String target = "";
                ArgusMethod method = null;
                boolean monitorParam = false;
                boolean monitorResult = false;
                boolean monitorTime = false;
                boolean monitorException = false;
                if (args.length == 1) {
                    uri = args[0];
                    if (!ArgusCache.hasUri(uri)) {
                        return new ExecuteResult(ArgusConstant.FAILED, "uri not exist!");
                    }
                    method = ArgusCache.getUriMethod(uri);
                }
                else if (args.length == 2) {
                    uri = args[0];
                    if (!ArgusCache.hasUri(uri)) {
                        return new ExecuteResult(ArgusConstant.FAILED, "uri not exist!");
                    }
                    method = ArgusCache.getUriMethod(uri);
                    target = args[1];
                    boolean contains = Arrays.asList("param", "result", "time", "ex").containsAll(Arrays.asList(target.split(",")));
                    if (!contains) {
                        return new ExecuteResult(ArgusConstant.FAILED, "target param error!");
                    }
                    if (target.contains("param")) {
                        monitorParam = true;
                    }
                    if (target.contains("result")) {
                        monitorResult = true;
                    }
                    if (target.contains("time")) {
                        monitorTime = true;
                    }
                    if (target.contains("ex")) {
                        monitorException = true;
                    }
                }

                ArgusCache.addMonitorInfo(new ArgusUser(request), new MonitorInfo(method, monitorParam, monitorResult, monitorTime, monitorException, true));
                return new ExecuteResult(ArgusConstant.SUCCESS, ArgusConstant.OK);
            }
        };

        Map<ArgusCommand, CommandExecutor> monitor = new HashMap<>();
        monitor.put(command, executor);
        return monitor;

    }
}
