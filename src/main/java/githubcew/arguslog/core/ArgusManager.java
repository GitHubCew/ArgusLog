package githubcew.arguslog.core;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.config.ArgusConfigurer;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cache.ArgusCacheManager;
import githubcew.arguslog.core.cmd.CommandManager;
import githubcew.arguslog.core.permission.ArgusPermissionConfigure;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorSender;
import githubcew.arguslog.monitor.trace.buddy.BuddyProxyManager;
import githubcew.arguslog.web.auth.ArgusAccountAuthenticator;
import githubcew.arguslog.web.auth.ArgusTokenAuthenticator;
import githubcew.arguslog.web.auth.TokenProvider;
import githubcew.arguslog.web.extractor.Extractor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Argus
 * 系统核心启动管理器
 *
 * 负责：
 *  - 初始化 Argus 核心组件
 *  - 注册命令
 *  - 扫描接口映射
 *  - 初始化监控与权限系统
 *  - 打印启动信息
 *
 * @author
 *   chenenwei
 */
@Component
public class ArgusManager implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ArgusManager.class);

    /** 初始化锁，确保只执行一次 */
    private static volatile boolean initialized = false;

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ArgusProperties argusProperties;
    @Autowired
    private Extractor extractor;
    @Autowired
    private UserProvider userProvider;
    @Autowired
    private TokenProvider tokenProvider;
    @Autowired
    private CommandManager commandManager;
    @Autowired
    private ArgusAccountAuthenticator argusAccountAuthenticator;
    @Autowired
    private ArgusTokenAuthenticator argusTokenAuthenticator;
    @Autowired
    private ArgusPermissionConfigure argusPermissionConfigure;
    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private ArgusCacheManager argusCacheManager;
    private MonitorSender monitorSender;
    private List<ArgusConfigurer> configurers = new ArrayList<>();

    public Extractor getExtractor() {
        return extractor;
    }

    public UserProvider getUserProvider() {
        return userProvider;
    }

    public TokenProvider getTokenProvider() {
        return tokenProvider;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public ArgusAccountAuthenticator getAccountAuthenticator() {
        return argusAccountAuthenticator;
    }

    public ArgusTokenAuthenticator getTokenAuthenticator() {
        return argusTokenAuthenticator;
    }

    public ArgusCacheManager getArgusCacheManager() {
        return argusCacheManager;
    }

    public MonitorSender getMonitorSender() {
        return monitorSender;
    }

    public ArgusPermissionConfigure getArgusPermissionConfigure () {
        return argusPermissionConfigure;
    }

    /**
     * 应用启动完成后初始化
     */
    @SneakyThrows
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        if (initialized) {
            return;
        }

        synchronized (ArgusManager.class) {
            if (initialized) {
                return;
            }

            this.configurers = new ArrayList<>(applicationContext.getBeansOfType(ArgusConfigurer.class).values());
            this.argusCacheManager = new ArgusCacheManager();
            this.monitorSender = new MonitorSender();

            // 初始化核心组件
            init();

            // 扫描接口映射
            scan();

            // 初始化 Buddy 代理机制
            BuddyProxyManager.init();

            // 初始化权限
            argusPermissionConfigure.init();

            // 打印 Argus 启动信息
            printArgusInfo();

            initialized = true;
            log.info("【Argus => ArgusManager initialized successfully after ApplicationReadyEvent...】");
        }
    }

    /**
     * 初始化核心组件
     */
    private void init() {
        // configurers 排序
        this.configurers.sort(AnnotationAwareOrderComparator.INSTANCE);

        // 注册命令
        registerCommand();

        // 启动缓存管理线程
        argusCacheManager.start();

        // 初始化监控线程池
        monitorSender.init();
    }

    /**
     * 注册命令
     */
    private void registerCommand() {
        for (ArgusConfigurer configurer : configurers) {
            try {
                configurer.registerCommand(this.commandManager);
            } catch (Exception e) {
                log.error("【Argus => Failed to register command from {}】", configurer.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 扫描接口映射
     */
    private void scan() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();

        handlerMethods.forEach((info, handlerMethod) -> {
            Set<String> urlPatterns = info.getPatternsCondition().getPatterns();
            Method method = handlerMethod.getMethod();

            for (String uri : urlPatterns) {
                if (!uri.startsWith("/")) {
                    uri = "/" + uri;
                }
                ArgusMethod argusMethod = new ArgusMethod(method);
                argusMethod.setMethod(method);
                argusMethod.setName(method.getName());
                argusMethod.setSignature(CommonUtil.generateSignature(method));
                argusMethod.setUri(uri);
                ArgusCache.addUriMethod(uri, argusMethod);
            }
        });

        log.info("【Argus => Mapped {} API endpoints】", handlerMethods.size());
    }

    /**
     * 打印 Argus 信息（Banner + 用户）
     */
    private void printArgusInfo() {

        // 打印 banner
        if (this.argusProperties.isPrintBanner()) {
            try (InputStream inputStream = new ClassPathResource("META-INF/resources/argus/banner.txt").getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                }
            } catch (Exception e) {
                log.warn("【Argus => Banner not found or failed to load】", e);
            }
        }

        // 打印用户信息
        if (this.argusProperties.isPrintUserInfo() && userProvider instanceof ArgusUserProvider) {
            ArgusUserProvider provider = (ArgusUserProvider) userProvider;
            log.info("【Argus => username: {}, password: {}】", provider.getUsername(), provider.getPassword());
        }
    }
}

