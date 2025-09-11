package githubcew.arguslog.core.cmd.monitor;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.util.*;

/**
 * 监控命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "monitor",
        description = "监听接口参数、耗时、结果、异常等数据",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class MonitorCmd extends BaseCommand {

    @CommandLine.Parameters(
            description = "接口路径",
            index = "0",
            arity = "0..1",
            paramLabel = "path"

    )
    private String path;

    @CommandLine.Option(
            names = {"-a", "--all"},
            description = "监听全部接口",
            arity = "0",
            fallbackValue = "true"
    )
    private boolean all;

    @CommandLine.Option(
            names = {"-t", "--total"},
            description = "不传参数时, 监听全部target, 多个参数用空格隔开",
            arity = "0",
            fallbackValue = "true"
    )
    private boolean allTarget;


    @CommandLine.Parameters(
            description = "监听接口目标参数， 可选：param,methodParam,result,time,header,ip,url,api,type,method",
            index = "1",
            arity = "0..*",
            paramLabel = "targets"
    )
    private List<String> targets;


    private final Set<String> MONITOR_TARGETS = new HashSet<>(Arrays.asList(
            "header", "ip", "param", "methodParam", "result", "time", "url", "api", "method", "type"));

    /**
     * 执行逻辑
     * @return 状态码
     * @throws Exception 异常
     */
    @Override
    protected Integer execute() throws Exception {

        Thread.sleep(2000);
        MonitorInfo monitorInfo = new MonitorInfo();
        // 监听全部接口
        if (all) {
            // 如果接口路径参数不为空, 则处理为 targets
            if (!Objects.isNull(path) && Objects.isNull(targets)) {
                targets = new ArrayList<>();
            }
            if(!Objects.isNull(path)){
                targets.add(path);
            }
            path = "*";
        } else {
            if (Objects.isNull(path) || path.isEmpty()) {
                throw new RuntimeException(ERROR_PATH_EMPTY);
            }
            if (path.equals("*")) {
                throw new RuntimeException(ERROR_PATH_NOT_FOUND);
            }
        }
        if (allTarget && Objects.isNull(targets)) {
            monitorAll(monitorInfo);
        }
        else {
            if (!Objects.isNull(targets) && !targets.isEmpty()) {
                monitorTargets(monitorInfo);
            } else {
                monitorDefault(monitorInfo);
            }
        }

        // 监听接口
        ArgusCache.addMonitorInfo(ArgusUserContext.getCurrentUsername(), monitorInfo, path);
        return OK_CODE;
    }

    /**
     * 创建监控信息
     *
     * @return 监控信息对象
     */
    private MonitorInfo createMonitorInfo() {
        MonitorInfo monitorInfo = new MonitorInfo();
        // 监听全部
        if (allTarget) {
            monitorAll(monitorInfo);
        }
        // 监听默认选项
        else if (Objects.isNull(targets) || targets.isEmpty()) {
            // 默认监听
            monitorDefault(monitorInfo);
        } else {
            // 监听指定目标
            monitorTargets(monitorInfo);
        }
        return monitorInfo;
    }

    /**
     * 设置默认监控选项（不监控调用链）
     *
     * @param monitorInfo 监控信息对象
     */
    private void monitorDefault(MonitorInfo monitorInfo) {
        monitorInfo.setUrl(true);
        monitorInfo.setIp(false);
        monitorInfo.setHeader(false);
        monitorInfo.setParam(true);
        monitorInfo.setMethodParam(false);
        monitorInfo.setResult(true);
        monitorInfo.setTime(true);
    }

    private void monitorAll(MonitorInfo monitorInfo) {
        monitorInfo.setUrl(true);
        monitorInfo.setApi(true);
        monitorInfo.setIp(true);
        monitorInfo.setHeader(true);
        monitorInfo.setParam(true);
        monitorInfo.setMethodParam(true);
        monitorInfo.setResult(true);
        monitorInfo.setTime(true);
        monitorInfo.setType(true);
        monitorInfo.setMethod(true);
    }

    /**
     * 设置指定监控目标
     *
     * @param monitorInfo 监控信息对象
     */
    private void monitorTargets(MonitorInfo monitorInfo) {

        // 验证目标参数
        if (!Objects.isNull(targets) && !targets.isEmpty() && !MONITOR_TARGETS.containsAll(targets)) {
            throw new RuntimeException("Targets not correct, available target : \n" + String.join(" ", MONITOR_TARGETS));
        }

        // 设置监控目标
        Set<String> targetSet = new HashSet<>(targets);
        monitorInfo.setHeader(targetSet.contains("header"));
        monitorInfo.setIp(targetSet.contains("ip"));
        monitorInfo.setParam(targetSet.contains("param"));
        monitorInfo.setMethodParam(targetSet.contains("methodParam"));
        monitorInfo.setResult(targetSet.contains("result"));
        monitorInfo.setTime(targetSet.contains("time"));
        monitorInfo.setUrl(targetSet.contains("url"));
        monitorInfo.setApi(targetSet.contains("api"));
        monitorInfo.setType(targetSet.contains("type"));
        monitorInfo.setMethod(targetSet.contains("method"));
    }
}
