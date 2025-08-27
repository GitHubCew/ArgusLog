package githubcew.arguslog.core.cmd;

import githubcew.arguslog.config.ArgusConfigurer;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.web.ArgusRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Argus 内置命令
 *
 * @author chenenwei
 */
@Order(-Integer.MAX_VALUE)
@Component
public class ArgusCommandRegistry implements ArgusConfigurer {

    private CommandManager commandManager;

    /**
     * 注册命令
     *
     * @param commandManager 命令管理器
     */
    @Override
    public void registerCommand(CommandManager commandManager) {

        if (this.commandManager == null) {
            this.commandManager = commandManager;
        }
        // 添加终端命令
        this.commandManager.register(help());
        this.commandManager.register(connect());
        this.commandManager.register(exit());
        this.commandManager.register(clear());
        this.commandManager.register(logout());

        // 添加Argus内置方法监听命令
        this.commandManager.register(ls());
        this.commandManager.register(monitor());
        this.commandManager.register(remove());
    }

    /**
     * 注册不需要认证的命令
     *
     * @param commandManager 命令管理器
     */
    @Override
    public void registerUnauthorizedCommands(CommandManager commandManager) {
        if (this.commandManager == null) {
            this.commandManager = commandManager;
        }
        this.commandManager.registerUnauthorizedCommands(help().keySet());
    }

    /**
     * 构建命令
     *
     * @param argusCommand 命令
     * @param executor     执行器
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> buildCommand(ArgusCommand argusCommand, CommandExecutor executor) {
        return Collections.singletonMap(argusCommand, executor);
    }

    /**
     * 连接connect
     *
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> connect() {
        ArgusCommand connect = new ArgusCommand(
                "connect",
                "连接 argus",
                "connect",
                "connect"
        );

        return buildCommand(connect, null);
    }

    /**
     * 断开 Argus 连接
     *
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> exit() {
        ArgusCommand exit = new ArgusCommand(
                "exit",
                "断开 argus 连接",
                "exit",
                "exit"
        );

        return buildCommand(exit, null);
    }

    /**
     * 断开 Argus 连接
     *
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> logout() {
        ArgusCommand logout = new ArgusCommand(
                "logout",
                "退出登录",
                "logout",
                "logout"
        );

        return buildCommand(logout, null);
    }

    /**
     * 清除终端页面数据
     *
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> clear() {
        ArgusCommand clear = new ArgusCommand(
                "clear",
                "清除终端",
                "clear",
                "clear"
        );

        return buildCommand(clear, null);
    }

    /**
     * help 命令
     *
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> help() {

        ArgusCommand help = new ArgusCommand(
                "help",
                "查看命令用法",
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
                    return ExecuteResult.failed(ArgusCommand.PARAM_ERROR);
                }
                List<ArgusCommand> commands;
                HelpFormatter helpFormatter = new HelpFormatter();
                if (args.length == 1) {
                    commands = commandManager.getCommands().stream().filter(c -> c.getCmd().equals(args[0])).sorted().collect(Collectors.toList());
                    if (commands.size() == 0) {
                        return ExecuteResult.failed(ArgusCommand.COMMAND_NOT_FOUND);
                    }
                    String result = helpFormatter.formatHelpDetail(commands.get(0));
                    return ExecuteResult.success(result);
                } else {
                    String result = helpFormatter.formatHelp(commandManager.getCommands());
                    return ExecuteResult.success(result);
                }
            }
        };
        return buildCommand(help, executor);

    }

    /**
     * ls命令
     *
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> ls() {
        ArgusCommand ls = new ArgusCommand(
                "ls",
                "列出接口列表",
                "ls [-m] [path], -m: 用户监听的接口 path: 接口路径，支持 '*' 匹配",
                "ls /api/v1; ls /api/*; ls /api/*/info");
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return "ls".equals(command);
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();
                if (args.length > 2) {
                    return ExecuteResult.failed(ArgusCommand.PARAM_ERROR);
                }
                if (args.length > 0) {
                    String arg = args[0];
                    if (arg.equals("-m")) {
                        return listMonitor(request.getToken().getToken(), args);
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
            private ExecuteResult listMonitor(String argusUser, String[] args) {
                String pattern = "*";
                if (args.length > 1) {
                    pattern = args[1];
                }
                List<String> dataList = ArgusCache.getUserMonitorUris(argusUser, pattern);
                String wrapperCopy = OutputWrapper.wrapperCopy(dataList, OutputWrapper.LINE_SEPARATOR);
                return ExecuteResult.success(String.join(OutputWrapper.LINE_SEPARATOR, wrapperCopy));
            }

            /**
             * 查询接口路径
             * @param args 参数
             * @return 结果
             */
            private ExecuteResult list(String[] args) {
                String pattern = "*";
                if (args.length > 0) {
                    pattern = args[0];
                }
                List<String> dataList = ArgusCache.getUrisWithPattern(pattern);
                String wrapperCopy = OutputWrapper.wrapperCopy(dataList, OutputWrapper.LINE_SEPARATOR);
                return ExecuteResult.success(String.join(OutputWrapper.LINE_SEPARATOR, wrapperCopy));
            }
        };
        return buildCommand(ls, executor);
    }

    /**
     * monitor命令
     *
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> monitor() {
        ArgusCommand monitor = new ArgusCommand(
                "monitor",
                "监听指定或者全部接口参数、执行结果、耗时、调用链",
                "monitor <path> [target], path: 接口路径,支持 '*' 匹配, [target]:可选值为：param（参数）,result（结果）,time（耗时）,chain（调用链）",
                "monitor /api/v1/demo param,result; monitor *; monitor /api/*/info param,time,result"
        );

        CommandExecutor executor = new CommandExecutor() {
            private final Set<String> MONITOR_TARGETS = new HashSet<>(Arrays.asList("param", "result", "time", "chain"));

            @Override
            public boolean supports(String command) {
                return "monitor".equals(command);
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();

                if (args.length == 0 || args.length > 2) {
                    return ExecuteResult.failed(ArgusCommand.PARAM_ERROR);
                }

                String pattern = args[0];
                try {
                    String user = request.getToken().getToken();
                    MonitorInfo monitorInfo = createMonitorInfo(args);
                    // 监听接口
                    if (Objects.isNull(monitorInfo.getMethod())) {
                        ArgusCache.addMonitorInfo(user, monitorInfo, pattern);
                    }
                    return ExecuteResult.success(ExecuteResult.OK);
                } catch (IllegalArgumentException e) {
                    return ExecuteResult.failed(e.getMessage());
                }
            }

            /**
             * 监听方法信息
             * @param args 参数
             * @return 监听信息
             */
            private MonitorInfo createMonitorInfo(String[] args) {

                MonitorInfo monitorInfo = new MonitorInfo();
                if (args.length == 1) {
                    // 默认监控所有目标(除调用链)
                    monitorAllWithoutCallChain(monitorInfo);
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
                monitorInfo.setCallChain(true);
            }

            /**
             * 监听全部信息，不监听调用链
             * @param monitorInfo 监听方法信息
             */
            private void monitorAllWithoutCallChain(MonitorInfo monitorInfo) {
                monitorInfo.setParam(true);
                monitorInfo.setResult(true);
                monitorInfo.setTime(true);
                monitorInfo.setException(true);
                monitorInfo.setCallChain(false);
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
                monitorInfo.setCallChain(targetSet.contains("chain"));
            }
        };

        return buildCommand(monitor, executor);
    }

    /**
     * 移除命令
     *
     * @return map
     */
    private Map<ArgusCommand, CommandExecutor> remove() {

        ArgusCommand command = new ArgusCommand(
                "remove",
                "移除用户已监听接口",
                "remove <path>, <path>: 接口路径,支持 '*' 匹配",
                "remove /api/v1/demo, remove *, remove /ap1/*"
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
                    return ExecuteResult.failed(ArgusCommand.PARAM_ERROR);
                }
                String pattern = args[0];
                String user = request.getToken().getToken();
                ArgusCache.removeMonitorMethodWithPattern(user, pattern);
                return ExecuteResult.success(ExecuteResult.OK);
            }
        };

        return buildCommand(command, executor);
    }

    /**
     * help 命令格式化器
     */
    public static class HelpFormatter {

        /**
         * 获取所有命令的简要帮助
         * 输出格式：命令名 + 描述，对齐排列
         *
         * @param commands 命令列表
         * @return 格式化输出
         */
        public String formatHelp(List<ArgusCommand> commands) {

            // 计算最大命令长度，用于对齐
            int maxCmdLength = commands.stream()
                    .mapToInt(c -> c.getCmd().length())
                    .max()
                    .orElse(4);

            // 建议最小宽度，避免太紧凑
            int cmdColumnWidth = Math.max(maxCmdLength + 2, 10);

            StringBuilder sb = new StringBuilder();
            sb.append("=== 可用命令 ===\n");

            for (ArgusCommand cmd : commands) {
                sb.append(String.format("%-" + cmdColumnWidth + "s%s%n",
                        cmd.getCmd(),
                        cmd.getIntroduction()));
            }

            return sb.toString().trim();
        }

        /**
         * 获取指定命令的详细帮助
         *
         * @param command 指定命令
         * @return 详细帮助
         */
        public String formatHelpDetail(ArgusCommand command) {

            String sb = String.format("=== %s 命令介绍 ===%n", command.getCmd()) +
                    String.format("%-8s%s%n", "介绍:", pad(command.getIntroduction())) +
                    String.format("%-8s%s%n", "用法:", pad(command.getUsage())) +
                    String.format("%-8s%s%n", "示例:", pad(command.getExample()));

            return sb.trim();
        }

        /**
         * 辅助方法：为多行文本添加缩进（避免 printf 对齐错位）
         *
         * @param text 多行文本
         * @return 缩进后的文本
         */
        private String pad(String text) {
            if (text == null || text.isEmpty()) {
                return "";
            }
            return text.replace("\n", "\n        ");
        }

        public static void main(String[] args) {

            ArgusCommand monitor = new ArgusCommand(
                    "monitor",
                    "监听接口 -a: 监听全部接口, path: 接口路径, [target]:可选值为：param（参数）,result（结果）,time（耗时）,ex（异常）",
                    "monitor [-a | path] [target]",
                    "monitor /api/v1/demo param,result"
            );
            ArgusCommand remove = new ArgusCommand(
                    "remove",
                    "移除监听接口,[-a]: 移除监听全部接口, <path>: 接口路径",
                    "remove [-a] <path>",
                    "remove /api/v1/demo, remove -a"
            );

            ArgusCommand clear = new ArgusCommand(
                    "connect",
                    "连接 argus",
                    "connect",
                    "connect"
            );

            HelpFormatter formatter = new HelpFormatter();
            System.out.println(formatter.formatHelp(Arrays.asList(monitor, remove, clear)));
            System.out.println(formatter.formatHelpDetail(clear));

        }
    }
}
