package githubcew.arguslog.core.cmd;

import githubcew.arguslog.config.ArgusConfigurer;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.monitor.trace.TraceEnhanceManager;
import githubcew.arguslog.monitor.trace.asm.AsmMethodCallExtractor;
import githubcew.arguslog.monitor.trace.asm.MethodCallInfo;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.auth.Token;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Argus 内置命令注册中心
 * 负责注册和管理所有系统内置命令
 *
 * @author chenenwei
 */
@Order(-Integer.MAX_VALUE)
@Component
public class ArgusCommandRegistry implements ArgusConfigurer {

    @Autowired
    private ArgusProperties argusProperties;

    private CommandManager commandManager;

    /**
     * 注册所有内置命令到命令管理器
     *
     * @param commandManager 命令管理器实例
     */
    @Override
    public void registerCommand(CommandManager commandManager) {
        if (this.commandManager == null) {
            this.commandManager = commandManager;
        }

        // 注册终端基础命令
        registerBasicCommands();

        // 注册监控相关命令
        registerMonitorCommands();

        // 注册方法调用链相关命令
        registerTraceCommands();
    }

    /**
     * 注册不需要认证的命令
     *
     * @param commandManager 命令管理器实例
     */
    @Override
    public void registerUnauthorizedCommands(CommandManager commandManager) {
        if (this.commandManager == null) {
            this.commandManager = commandManager;
        }
        this.commandManager.registerUnauthorizedCommands(help().keySet());
    }

    /**
     * 注册终端基础命令
     */
    private void registerBasicCommands() {
        this.commandManager.register(help());
        this.commandManager.register(connect());
        this.commandManager.register(exit());
        this.commandManager.register(clear());
        this.commandManager.register(logout());
    }

    /**
     * 注册监控相关命令
     */
    private void registerMonitorCommands() {
        this.commandManager.register(ls());
        this.commandManager.register(monitor());
        this.commandManager.register(remove());
    }

    /**
     * 注册方法调用链相关命令
     */
    private void registerTraceCommands() {
        this.commandManager.register(trace());
        this.commandManager.register(revert());
    }

    /**
     * 构建命令映射
     *
     * @param argusCommand 命令定义
     * @param executor     命令执行器
     * @return 命令映射表
     */
    private Map<ArgusCommand, CommandExecutor> buildCommand(ArgusCommand argusCommand, CommandExecutor executor) {
        return Collections.singletonMap(argusCommand, executor);
    }

    /**
     * 创建连接命令
     *
     * @return 连接命令映射
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
     * 创建断开连接命令
     *
     * @return 断开连接命令映射
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
     * 创建退出登录命令
     *
     * @return 退出登录命令映射
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
     * 创建清除终端命令
     *
     * @return 清除终端命令映射
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
     * 创建帮助命令
     *
     * @return 帮助命令映射
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

                HelpFormatter helpFormatter = new HelpFormatter();
                if (args.length == 1) {
                    List<ArgusCommand> commands = commandManager.getCommands().stream()
                            .filter(c -> c.getCmd().equals(args[0]))
                            .sorted()
                            .collect(Collectors.toList());

                    if (commands.isEmpty()) {
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
     * 创建列表命令
     *
     * @return 列表命令映射
     */
    private Map<ArgusCommand, CommandExecutor> ls() {
        ArgusCommand ls = new ArgusCommand(
                "ls",
                "列出接口列表",
                "ls [-v] [path], -v: 查看用户监听的接口 path: 接口路径，支持 '*' 匹配",
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

                // 查询监听的接口
                if (args.length > 0 && args[0].equals("-v")) {
                    return listMonitor(request.getToken().getToken(), args);
                }

                // 查询接口
                return list(args);
            }

            /**
             * 查询用户监听接口
             *
             * @param argusUser 用户名
             * @param args 命令参数
             * @return 执行结果
             */
            private ExecuteResult listMonitor(String argusUser, String[] args) {
                String pattern = "*";
                if (args.length > 1) {
                    pattern = args[1];
                }

                List<String> dataList = ArgusCache.getUserMonitorUris(argusUser, pattern);
                long total = dataList.size();
                if (dataList.size() > 100) {
                    dataList = dataList.subList(0, 100);
                }

                OutputWrapper outputWrapper = OutputWrapper.wrapperCopyV2(dataList, OutputWrapper.LINE_SEPARATOR);
                if (total > 0) {
                    outputWrapper.newLine().append(" (" + total + ")").newLine();
                }

                return ExecuteResult.success(String.join(OutputWrapper.LINE_SEPARATOR, outputWrapper.build()));
            }

            /**
             * 查询接口路径
             *
             * @param args 命令参数
             * @return 执行结果
             */
            private ExecuteResult list(String[] args) {
                String pattern = "*";
                if (args.length > 0) {
                    pattern = args[0];
                }

                List<String> dataList = ArgusCache.getUrisWithPattern(pattern);
                long total = dataList.size();
                if (dataList.size() > 100) {
                    dataList = dataList.subList(0, 100);
                }

                OutputWrapper outputWrapper = OutputWrapper.wrapperCopyV2(dataList, OutputWrapper.LINE_SEPARATOR);
                if (total > 0) {
                    outputWrapper.newLine().append(" (" + total + ")").newLine();
                }

                return ExecuteResult.success(String.join(OutputWrapper.LINE_SEPARATOR, outputWrapper.build()));
            }
        };

        return buildCommand(ls, executor);
    }

    /**
     * 创建监控命令
     *
     * @return 监控命令映射
     */
    private Map<ArgusCommand, CommandExecutor> monitor() {
        ArgusCommand monitor = new ArgusCommand(
                "monitor",
                "监听指定或者全部接口参数、执行结果、耗时",
                "monitor <path> [target], path: 接口路径,支持 '*' 匹配, [target]: 可选值为：header（请求头）, ip(请求ip), param(前端请求参数), methodParam(方法参数), result（结果）,time（耗时）",
                "monitor /api/v1/demo param,result; monitor *; monitor /api/*/info param,time,result"
        );

        CommandExecutor executor = new CommandExecutor() {
            private final Set<String> MONITOR_TARGETS = new HashSet<>(Arrays.asList(
                    "header", "ip", "param", "methodParam", "result", "time"));

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
                    ArgusCache.addMonitorInfo(user, monitorInfo, pattern);
                    return ExecuteResult.success(ExecuteResult.OK);
                } catch (IllegalArgumentException e) {
                    return ExecuteResult.failed(e.getMessage());
                }
            }

            /**
             * 创建监控信息
             *
             * @param args 命令参数
             * @return 监控信息对象
             */
            private MonitorInfo createMonitorInfo(String[] args) {
                MonitorInfo monitorInfo = new MonitorInfo();
                if (args.length == 1) {
                    // 默认监听
                    monitorDefault(monitorInfo);
                } else {
                    // 监听指定目标
                    monitorTargets(monitorInfo, args[1]);
                }
                return monitorInfo;
            }

            /**
             * 设置默认监控选项（不监控调用链）
             *
             * @param monitorInfo 监控信息对象
             */
            private void monitorDefault(MonitorInfo monitorInfo) {
                monitorInfo.setIp(true);
                monitorInfo.setHeader(false);
                monitorInfo.setParam(true);
                monitorInfo.setMethodParam(true);
                monitorInfo.setResult(true);
                monitorInfo.setTime(true);
                monitorInfo.setException(true);
            }

            /**
             * 设置指定监控目标
             *
             * @param monitorInfo 监控信息对象
             * @param target 目标参数
             */
            private void monitorTargets(MonitorInfo monitorInfo, String target) {
                String[] targets = target.split(",");

                // 验证目标参数
                if (!MONITOR_TARGETS.containsAll(Arrays.asList(targets))) {
                    throw new IllegalArgumentException("param error!");
                }

                // 设置监控目标
                Set<String> targetSet = new HashSet<>(Arrays.asList(targets));
                monitorInfo.setHeader(targetSet.contains("header"));
                monitorInfo.setParam(targetSet.contains("param"));
                monitorInfo.setMethodParam(targetSet.contains("methodParam"));
                monitorInfo.setResult(targetSet.contains("result"));
                monitorInfo.setTime(targetSet.contains("time"));
                monitorInfo.setException(targetSet.contains("ex"));
            }
        };

        return buildCommand(monitor, executor);
    }

    /**
     * 创建移除命令
     *
     * @return 移除命令映射
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
     * 创建追踪命令
     *
     * @return 追踪命令映射
     */
    private Map<ArgusCommand, CommandExecutor> trace() {
        ArgusCommand trace = new ArgusCommand(
                "trace",
                "追踪接口的方法调用链,用于分析方法执行耗时",
                "trace [-v] <path> [include] [exclude], -v: 查看已追踪的接口, <path>: 接口路径, include: 显示包含的包, exclude: 排除包",
                "trace /api/v1/demo io.githubcew io.exclude; trace -v"
        );

        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return "trace".equals(command);
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();
                if (args.length < 1 || args.length > 3) {
                    return ExecuteResult.failed(ArgusCommand.PARAM_ERROR);
                }

                Token token = request.getToken();

                // 查看追踪的接口
                if (args[0].equals("-v")) {
                    List<String> dataList = ArgusCache.getTraceUriByUser(token.getToken());
                    long total = dataList.size();
                    if (dataList.size() > 100) {
                        dataList = dataList.subList(0, 100);
                    }

                    OutputWrapper outputWrapper = OutputWrapper.wrapperCopyV2(dataList, OutputWrapper.LINE_SEPARATOR);
                    if (total > 0) {
                        outputWrapper.newLine().append(" (" + total + ")").newLine();
                    }

                    return ExecuteResult.success(outputWrapper.build());
                }
                // 追踪接口
                else {
                    String uri = args[0];
                    if (!ArgusCache.hasUri(uri)) {
                        return ExecuteResult.failed("接口不存在");
                    }

                    Set<String> includePackages = new HashSet<>(1);
                    Set<String> excludePackages = new HashSet<>(argusProperties.getDefaultExcludePackages());

                    if (argusProperties.getExcludePackages() != null) {
                        excludePackages.addAll(argusProperties.getExcludePackages());
                    }
                    if (argusProperties.getIncludePackages() != null) {
                        includePackages.addAll(argusProperties.getIncludePackages());
                    }

                    // 包含包
                    if (args.length == 2) {
                        includePackages.addAll(Arrays.asList(args[1].split(",")));
                    }
                    // 排除包
                   if (args.length == 3) {
                        excludePackages.addAll(Arrays.asList(args[2].split( ",")));
                    }


                    if (includePackages.isEmpty()) {
                        return ExecuteResult.failed("至少需要输入一个过滤的包名");
                    }

                    ArgusMethod argusMethod = ArgusCache.getUriMethod(uri);
                    // 生成方法调用信息
                    Set<MethodCallInfo> methodCallInfos;
                    try {
                        methodCallInfos = AsmMethodCallExtractor.extractNestedCustomMethodCalls(
                                argusMethod.getMethod(),
                                includePackages,
                                excludePackages);
                    } catch (Exception e) {
                        return ExecuteResult.failed(e.getMessage());
                    }

                    if (methodCallInfos.size() > argusProperties.getMaxEnhancedClassNum()) {
                        return ExecuteResult.failed("需要增强的类过多,请缩小包的范围");
                    }

                    // 继承方法
                    Map<String, List<MethodCallInfo>> extendMethods = methodCallInfos.stream()
                            .filter(MethodCallInfo::isInherited)
                            .collect(Collectors.groupingBy(MethodCallInfo::getActualDefinedClass));

                    // 非继承方法
                    Map<String, List<MethodCallInfo>> nonExtendMethods = methodCallInfos.stream()
                            .filter(call -> !call.isInherited())
                            .collect(Collectors.groupingBy(MethodCallInfo::getSubCalledClass));

                    // 继承方法重定义类
                    redefine(argusMethod.getSignature(), extendMethods);

                    // 非继承方法重定义类
                    redefine(argusMethod.getSignature(), nonExtendMethods);

                    // 添加用户监听trace方法
                    ArgusCache.addUserTraceMethod(request.getToken().getToken(), argusMethod);

                    return ExecuteResult.success(ExecuteResult.OK);
                }
            }
        };

        return buildCommand(trace, executor);
    }

    /**
     * 创建回退命令
     *
     * @return 回退命令映射
     */
    private Map<ArgusCommand, CommandExecutor> revert() {
        ArgusCommand revert = new ArgusCommand(
                "revert",
                "回退（取消）方法调用链追踪",
                "revert <path> , <path>: 接口路径",
                "revert /api/v1/demo"
        );

        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean supports(String command) {
                return "revert".equals(command);
            }

            @Override
            public ExecuteResult execute(ArgusRequest request) {
                String[] args = request.getRequestCommand().getArgs();
                if (args.length != 1) {
                    return ExecuteResult.failed(ArgusCommand.PARAM_ERROR);
                }

                // 回退所有方法调用链追踪
                if (args[0].equals("*")) {
                    try {
                        // 回退全部方法调用链追踪
                        TraceEnhanceManager.revertAllClasses();
                        // 移除全部监听用户
                        ArgusCache.userRemoveAllTraceMethod(request.getToken().getToken());
                    } catch (Exception e) {
                        return ExecuteResult.failed("Revert error: " + e.getMessage());
                    }
                }
                // 回退指定方法追踪
                else {
                    String uri = args[0];
                    ArgusMethod uriMethod = ArgusCache.getUriMethod(uri);
                    if (uriMethod == null) {
                        return ExecuteResult.failed("接口不存在");
                    }

                    try {
                        // 回退方法调用链追踪
                        TraceEnhanceManager.revertClassWithKey(uriMethod.getSignature());
                        // 移除监听用户
                        ArgusCache.userRemoveTraceMethod(request.getToken().getToken(), uriMethod);
                    } catch (Exception e) {
                        return ExecuteResult.failed("Revert error: " + e.getMessage());
                    }
                }

                return ExecuteResult.success(ExecuteResult.OK);
            }
        };

        return buildCommand(revert, executor);
    }

    /**
     * 重定义方法
     *
     * @param methodKey 监听方法唯一标识
     * @param methods   方法映射表
     */
    private void redefine(String methodKey, Map<String, List<MethodCallInfo>> methods) {
        methods.forEach((className, callInfos) -> {
            try {
                Class<?> aClass = Class.forName(className.replace("/", "."));
                List<String> methodNames = callInfos.stream()
                        .map(MethodCallInfo::getCalledMethod)
                        .collect(Collectors.toList());

                TraceEnhanceManager.enhanceMethods(methodKey, aClass, methodNames);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 帮助命令格式化器
     * 用于格式化命令帮助信息的显示
     */
    public static class HelpFormatter {

        /**
         * 格式化所有命令的简要帮助信息
         *
         * @param commands 命令列表
         * @return 格式化后的帮助信息
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
         * 格式化指定命令的详细帮助信息
         *
         * @param command 指定命令
         * @return 格式化后的详细帮助信息
         */
        public String formatHelpDetail(ArgusCommand command) {
            String sb = String.format("=== %s 命令介绍 ===%n", command.getCmd()) +
                    String.format("%-8s%s%n", "介绍:", pad(command.getIntroduction())) +
                    String.format("%-8s%s%n", "用法:", pad(command.getUsage())) +
                    String.format("%-8s%s%n", "示例:", pad(command.getExample()));

            return sb.trim();
        }

        /**
         * 为多行文本添加缩进（避免 printf 对齐错位）
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

        /**
         * 测试方法
         *
         * @param args 命令行参数
         */
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