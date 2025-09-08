package githubcew.arguslog.core.cmd.trace;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.core.cmd.ColorWrapper;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.monitor.trace.TraceEnhanceManager;
import githubcew.arguslog.monitor.trace.asm.AsmMethodCallExtractor;
import githubcew.arguslog.monitor.trace.asm.MethodCallInfo;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author chenenwei
 */

@CommandLine.Command(
        name = "trace",
        description = "查看接口调用链",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class TraceCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "接口路径",
            arity = "0..1",
            paramLabel = "path"
    )
    private String path;

    @CommandLine.Option(
            names = {"-i", "--include"},
            description = "指定包名，只显示包含指定包名的方法",
            arity = "0..*",
            paramLabel = "package"
    )
    private List<String> includePackages;

    @CommandLine.Option(
            names = {"-e", "--exclude"},
            description = "排除包名，过滤掉指定包名的方法",
            arity = "0..*",
            paramLabel = "package"
    )
    private List<String> excludePackages;

    @CommandLine.Option(
            names = {"-m"},
            description = "查看已监听的调用链接口",
            arity = "0",
            fallbackValue = "true"
    )
    private boolean monitor;

    @CommandLine.Option(
            names = {"-t", "--threshold"},
            description = "指定调用链方法耗时阈值，单位ms",
            arity = "1"

    )
    private long threshold;

    @CommandLine.Option(
            names = {"-d", "--depth"},
            description = "调用链的最大的深度",
            arity = "1"
    )
    private int maxDepth;

    /**
     * 执行逻辑
     * @return 状态码
     * @throws Exception 异常
     */
    @Override
    protected Integer execute() throws Exception {

        try {
            // 查看已监听的接口
            if (monitor) {
                picocliOutput.out(view());
                return OK_CODE;
            }
            // 监听接口
            else {
                trace();
            }
        } catch (Exception e) {
            picocliOutput.error(e.getMessage());
            return ERROR_CODE;
        };
        return OK_CODE;
    }

    /**
     * 查看已监听的接口
     */
    private String view () {
        String currentUsername = ArgusUserContext.getCurrentUsername();
        List<String> dataList = ArgusCache.getTraceUriByUser(currentUsername);
        long total = dataList.size();
        if (dataList.size() > 50) {
            dataList = dataList.subList(0, 50);
        }

        OutputWrapper outputWrapper = OutputWrapper.wrapperCopyV2(dataList, OutputWrapper.LINE_SEPARATOR);
        if (total > 0) {
            outputWrapper.newLine().append(" (" + total + ")").newLine();
        }
        return outputWrapper.build();
    }

    /**
     * 追踪接口调用链
     */
    private void trace () {
        if (Objects.isNull(path)) {
            throw new RuntimeException(ERROR_PATH_EMPTY);
        }
        if (!ArgusCache.hasUri(path)) {
            throw new RuntimeException(ERROR_PATH_NOT_FOUND);
        }

        ArgusProperties argusProperties = ContextUtil.getBean(ArgusProperties.class);
        Set<String> includePackages = new HashSet<>(1);
        Set<String> excludePackages = new HashSet<>(argusProperties.getTraceDefaultExcludePackages());

        // 包含包
        if (!Objects.isNull(this.includePackages)) {
            includePackages.addAll(this.includePackages);
        }
        else {
            if (argusProperties.getTraceIncludePackages() != null) {
                includePackages.addAll(argusProperties.getTraceIncludePackages());
            }
        }

        // 排除包
        if (!Objects.isNull(this.excludePackages)) {
            excludePackages.addAll(this.excludePackages);
        }
        else {
            if (argusProperties.getTraceExcludePackages() != null) {
                excludePackages.addAll(argusProperties.getTraceExcludePackages());
            }
        }

        if (includePackages.isEmpty()) {
            throw new RuntimeException("至少要指定一个包名");
        }

        // 方法耗时颜色阈值
        if (threshold < 1) {
            threshold = argusProperties.getTraceColorThreshold();
        }
        // 最大深度
        if (maxDepth < 1) {
            maxDepth = argusProperties.getTraceMaxDepth();
        }

        ArgusMethod argusMethod = ArgusCache.getUriMethod(path);
        // 生成方法调用信息
        Set<MethodCallInfo> methodCallInfos = new LinkedHashSet<>();
        Set<String> skipClasses = new HashSet<>();
        try {
            methodCallInfos = AsmMethodCallExtractor.extractNestedCustomMethodCalls(
                    argusMethod.getMethod(),
                    includePackages,
                    excludePackages,
                    skipClasses,
                    maxDepth);
        } catch (Exception e) {
            picocliOutput.error(e.getMessage());
        }


        if (methodCallInfos.size() > argusProperties.getTraceMaxEnhancedClassNum()) {
            throw new RuntimeException("调用链方法过多,请缩小包的范围");
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

        String user = ArgusUserContext.getCurrentUsername();

        MonitorInfo monitorInfo = new MonitorInfo();
        monitorInfo.setArgusMethod(argusMethod);
        monitorInfo.setTrace(new MonitorInfo.Trace(threshold, maxDepth, argusProperties.getTraceColor(), methodCallInfos));
        ArgusCache.addUserTraceMethod(user, monitorInfo);

        picocliOutput.out(OK);
        if (skipClasses.size() > 0) {
            picocliOutput.out(ColorWrapper.yellow("\n跳过的类：\n" + String.join("\n", skipClasses)));
        }
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
}
