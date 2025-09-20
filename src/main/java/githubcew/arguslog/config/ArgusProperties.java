package githubcew.arguslog.config;

import githubcew.arguslog.core.anno.ArgusProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Argus配置
 *
 * @author chenenwei
 */
@ConfigurationProperties(prefix = "argus")
@Data
public class ArgusProperties {

    // 开启认证
    @ArgusProperty(description = "认证状态", modifyInRunning = true)
    private boolean enableAuth = true;

    // 默认用户名
    @ArgusProperty(description = "用户名", displayInShow = false)
    private String username = "argus";

    // 默认用户密码
    @ArgusProperty(description = "用户密码", displayInShow = false)
    private String password = "argus";

    // token 刷新时间（秒）
    @ArgusProperty(description = "token 刷新时间（秒）")
    private long tokenFlushTime = 60L;

    // 打印用户信息
    @ArgusProperty(description = "启动时打印用户信息")
    private boolean printUserInfo = true;

    // 打印argus banner
    @ArgusProperty(description = "启动时打印banner信息")
    private boolean printBanner = true;

    // token过期时间 （1小时）
    @ArgusProperty(description = "token过期时间(秒)")
    private long tokenExpireTime = 3600L;

    // 任务核心线程数
    @ArgusProperty(description = "任务核心线程数")
    private int threadCoreNum = 1;

    // 任务非核心线程数
    @ArgusProperty(description = "任务非核心线程数")
    private int threadNum = 3;

    // 任务队列最大等待数量
    @ArgusProperty(description = "任务队列最大等待数量")
    private int maxWaitQueueSize = 20;

    // 最大增强类数
    @ArgusProperty(description = "最大增强类数量", modifyInRunning = true)
    private int traceMaxEnhancedClassNum = 500;

    // 包含包
    @ArgusProperty(description = "包含包", modifyInRunning = true)
    private Set<String> traceIncludePackages;

    // 排除包
    @ArgusProperty(description = "排除包", modifyInRunning = true)
    private Set<String> traceExcludePackages;

    // 默认排除包
    @ArgusProperty(description = "默认排除包")
    private final Set<String> traceDefaultExcludePackages = new HashSet<>(Arrays.asList("sun.", "java.", "javax."));

    // 调用链最大深度
    @ArgusProperty(description = "调用链最大深度", modifyInRunning = true)
    private int traceMaxDepth = 6;

    // 调用链方法耗时阈值(ms)
    @ArgusProperty(description = "调用链方法耗时阈值(ms)", modifyInRunning = true)
    private long traceColorThreshold = 300;

    // trace增强处理线程数
    @ArgusProperty(description = "调用链增强处理线程数", modifyInRunning = true)
    private int traceMaxThreadNum = 5;

}
