package githubcew.arguslog.config;

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
    private boolean enableAuth = true;

    // 默认用户名
    private String username = "argus";

    // 默认用户密码
    private String password = "argus";

    // token 刷新时间（秒）
    private Long tokenFlushTime = 60L;

    // 打印用户信息
    private Boolean printUserInfo = true;

    // 打印argus banner
    private Boolean printBanner = true;

    // token过期时间 （30分钟）
    private Long tokenExpireTime = 1000 * 60 * 30L;

    // 处理的线程数
    private Integer threadNum = 3;

    // 队列最大等待数量
    private Integer maxWaitQueueSize = 20;

    // 最大增强类数
    private Integer traceMaxEnhancedClassNum = 100;

    // 包含包
    private Set<String> traceIncludePackages;

    // 排除包
    private Set<String> traceExcludePackages;

    // 默认排除包
    private final Set<String> traceDefaultExcludePackages =
            new HashSet<>(Arrays.asList("sun.", "java.", "javax."));

    // 最大深度
    private int traceMaxDepth = 6;

    // 调用链颜色阈值(ms)
    private long traceColorThreshold = 300;
}
