package githubcew.arguslog.core.cmd;

import githubcew.arguslog.core.ArgusCache;
import githubcew.arguslog.core.ArgusConfigurer;
import githubcew.arguslog.core.ArgusConstant;
import githubcew.arguslog.core.ArgusRequest;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.method.ArgusMethod;
import githubcew.arguslog.core.method.MonitorInfo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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

        if (this.commandManager == null) {
            this.commandManager = commandManager;
        }
        // 添加Argus内置命令
        this.commandManager.register(help());
        this.commandManager.register(ls());
        this.commandManager.register(monitor());
        this.commandManager.register(remove());
    }

    @Override
    public void registerUnauthorizedCommands(CommandManager commandManager) {
        if (this.commandManager == null) {
            this.commandManager = commandManager;
        }
        this.commandManager.registerUnauthorizedCommands(help().keySet());
    }

    private Map<ArgusCommand, CommandExecutor> buildCommand (ArgusCommand argusCommand, CommandExecutor executor) {
        return Collections.singletonMap(argusCommand, executor);
    }

    /**
     * help 命令
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> help () {

        ArgusCommand command = new ArgusCommand(
                "help",
                "命令用法查看， [command]: 命令",
                "help [command]",
                "help ls");
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return "help".equals(command);
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();
                if (args.length > 1) {
                    return new ExecuteResult(ArgusConstant.FAILED, "param error! usage: help [command]");
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
        return buildCommand(command, executor);

    }

    /**
     * ls命令
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> ls () {
        ArgusCommand command = new ArgusCommand(
                "ls",
                "列出接口列表 [-m]: 过滤已监听的接口, [path]: 接口uri",
                "ls [-m] [path]",
                "ls /api/v1");
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return "ls".equals(command);
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();
                if (args.length > 2) {
                    return new ExecuteResult(ArgusConstant.FAILED, "param error! usage: ls [-m] <path>");
                }
                if (args.length > 0) {
                    String arg = args[0];
                    if (arg.equals("-m")) {
                        return listMonitor(new ArgusUser(request), args);
                    }
                }
                return list(args);
            }

            /**
             * 查询用户监听接口
             * @param argusUser 用户
             * @param args 参数
             * @return 结果
             */
            private ExecuteResult listMonitor (ArgusUser argusUser, String[] args) {
                String uri = "";
                if (args.length > 1) {
                    uri = args[1];
                }
                return new ExecuteResult(ArgusConstant.SUCCESS, String.join(ArgusConstant.LINE_SEPARATOR, ArgusCache.getUserMonitorUris(argusUser, uri)));
            }

            /**
             * 查询接口路径
             * @param args 参数
             * @return 结果
             */
            private ExecuteResult list (String[] args) {
                String uri = "";
                if (args.length > 0) {
                    uri = args[0];
                }
                List<String> data = ArgusCache.getUris(uri);
                return new ExecuteResult(ArgusConstant.SUCCESS, String.join(ArgusConstant.LINE_SEPARATOR, data));
            }
        };
        return buildCommand(command, executor);
    }

    /**
     * monitor命令
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> monitor() {
        ArgusCommand command = new ArgusCommand(
                "monitor",
                "监听接口 -a: 监听全部接口, path: 接口路径, [target]:可选值为：param（参数）,result（结果）,time（耗时）,ex（异常）",
                "monitor [-a | path] [target]",
                "monitor /api/v1/demo param,result"
        );

        CommandExecutor executor = new CommandExecutor() {
            private final Set<String> MONITOR_TARGETS = new HashSet<>(Arrays.asList("param", "result", "time", "ex"));

            @Override
            public boolean supports(String command) {
                return "monitor".equals(command);
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();

                if (args.length == 0 || args.length > 2) {
                    return new ExecuteResult(ArgusConstant.FAILED, "param error! usage: monitor [-a | path] [target]");
                }

                if (!(args[0].equals("-a") || ArgusCache.hasUri(args[0]))) {
                    return new ExecuteResult(ArgusConstant.FAILED, "param error or uri not found: " + args[0]);
                }

                try {
                    ArgusUser user = new ArgusUser(request);
                    MonitorInfo monitorInfo = createMonitorInfo(args[0], args);
                    // 监控全部
                    if (Objects.isNull(monitorInfo.getMethod())) {
                        ArgusCache.addAllMonitorInfo(user, monitorInfo);
                    }
                    // 监控指定方法
                    else {
                        ArgusCache.addMonitorInfo(new ArgusUser(request), monitorInfo);
                    }
                    return new ExecuteResult(ArgusConstant.SUCCESS, ArgusConstant.OK);
                } catch (IllegalArgumentException e) {
                    return new ExecuteResult(ArgusConstant.FAILED, e.getMessage());
                }
            }

            /**
             * 监听方法信息
             * @param type type
             * @param args 参数
             * @return 监听信息
             */
            private MonitorInfo createMonitorInfo(String type, String[] args) {
                ArgusMethod method = null;
                MonitorInfo monitorInfo = new MonitorInfo();

                if (!type.equals("-a")) {
                    method = ArgusCache.getUriMethod(type);
                    monitorInfo.setMethod(method);
                }

                if (args.length == 1) {
                    // 默认监控所有目标
                    monitorAll(monitorInfo);
                } else {
                    // 解析指定的监控目标
                    monitorTargets(monitorInfo, args[1]);
                }

                return monitorInfo;
            }

            /**
             * 监听全部信息
             * @param monitorInfo 监听方法信息
             */
            private void monitorAll(MonitorInfo monitorInfo) {
                monitorInfo.setParam(true);
                monitorInfo.setResult(true);
                monitorInfo.setTime(true);
                monitorInfo.setException(true);
            }

            /**
             * 监控方法目标信息
             * @param monitorInfo 监听方法信息
             * @param target 目标信息
             */
            private void monitorTargets(MonitorInfo monitorInfo, String target) {
                String[] targets = target.split(",");

                // 验证目标参数
                if (!MONITOR_TARGETS.containsAll(Arrays.asList(targets))) {
                    throw new IllegalArgumentException("param error!");
                }

                // 设置监控目标
                Set<String> targetSet = new HashSet<>(Arrays.asList(targets));
                monitorInfo.setParam(targetSet.contains("param"));
                monitorInfo.setResult(targetSet.contains("result"));
                monitorInfo.setTime(targetSet.contains("time"));
                monitorInfo.setException(targetSet.contains("ex"));
            }
        };

        return buildCommand(command, executor);
    }

    /**
     * 移除命令
     * @return  map
     */
    private Map<ArgusCommand, CommandExecutor> remove() {

        ArgusCommand command = new ArgusCommand(
                "remove",
                "移除监听接口,[-a]: 移除监听全部接口, <path>: 接口路径",
                "remove [-a] <path>",
                "remove /api/v1/demo, remove -a"
        );

        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return "remove".equals(command);
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {

                String[] args = request.getRequestCommand().getArgs();
                if (args.length != 1) {
                    return new ExecuteResult(ArgusConstant.FAILED, "param error! usage: remove [-a | <path>]");
                }
                ArgusUser user = new ArgusUser(request);
                if (args[0].equals("-a")) {
                    ArgusCache.userRemoveAllMethod(user);
                    return new ExecuteResult(ArgusConstant.SUCCESS, ArgusConstant.OK);
                }
                if(!ArgusCache.hasUri(args[0])) {
                    return new ExecuteResult(ArgusConstant.FAILED, "uri not found: " + args[0]);
                }
                ArgusCache.userRemoveMethod(user, ArgusCache.getUriMethod(args[0]));
                return new ExecuteResult(ArgusConstant.SUCCESS, ArgusConstant.OK);
            }
        };

        return buildCommand(command, executor);
    }
}
