package githubcew.arguslog.core;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.config.ArgusConfigurer;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cache.ArgusCacheManager;
import githubcew.arguslog.core.cmd.CommandManager;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorSender;
import githubcew.arguslog.monitor.trace.buddy.BuddyProxyManager;
import githubcew.arguslog.monitor.trace.jdk.JdkProxyWrapper;
import githubcew.arguslog.web.auth.ArgusAccountAuthenticator;
import githubcew.arguslog.web.auth.ArgusTokenAuthenticator;
import githubcew.arguslog.web.auth.TokenProvider;
import githubcew.arguslog.web.extractor.Extractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
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
 *
 * @author chenenwei
 */
@Component
public class ArgusManager implements ApplicationListener<ContextRefreshedEvent>, CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ArgusManager.class);

    // 初始化锁
    private static volatile boolean initialized = false;

    private ArgusProperties argusProperties;
    private Extractor extractor;
    private UserProvider userProvider;
    private TokenProvider tokenProvider;
    private List<ArgusConfigurer> configurers = new ArrayList<>();
    private CommandManager commandManager;
    private ApplicationContext applicationContext;
    private ArgusAccountAuthenticator argusAccountAuthenticator;
    private ArgusTokenAuthenticator argusTokenAuthenticator;
    private ArgusCacheManager argusCacheManager;
    private MonitorSender monitorSender;


    private RequestMappingHandlerMapping requestMappingHandlerMapping;


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

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        // 确保只执行一次
        if (initialized) {
            return;
        }

        synchronized (ArgusManager.class) {
            if (initialized) {
                return;
            }

            this.applicationContext = event.getApplicationContext();
            this.configurers = new ArrayList<>(applicationContext.getBeansOfType(ArgusConfigurer.class).values());
            this.commandManager = applicationContext.getBean(CommandManager.class);
            this.extractor = applicationContext.getBean(Extractor.class);
            this.userProvider = applicationContext.getBean(UserProvider.class);
            this.tokenProvider = applicationContext.getBean(TokenProvider.class);
            this.requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            this.argusProperties = applicationContext.getBean(ArgusProperties.class);
            this.argusAccountAuthenticator = applicationContext.getBean(ArgusAccountAuthenticator.class);
            this.argusTokenAuthenticator = applicationContext.getBean(ArgusTokenAuthenticator.class);
            this.argusCacheManager = new ArgusCacheManager();
            this.monitorSender = new MonitorSender();

            // 注册bean
            init();

            // 扫描接口
            scan();

            // 初始化 buddy
            BuddyProxyManager.init();

            initialized = true;

            // 打印Argus启动信息
            printArgusInfo();

            log.info("【Argus => ArgusManager initialized successfully...】");
        }
    }

    /**
     * 初始化
     */
    public void init() {

        // configurers 排序
        this.configurers.sort(AnnotationAwareOrderComparator.INSTANCE);

        // 注册命令
        registerCommand();

        // 开启线程
        argusCacheManager.start();

        // 开启线程池
        monitorSender.init();

    }

    /**
     * 注册命令
     */
    private void registerCommand() {

        for (ArgusConfigurer configurer : configurers) {
            configurer.registerCommand(this.commandManager);
        }
    }

    /**
     * 扫描接口
     */
    private void scan() {
        // 获取Spring MVC中所有的RequestMapping信息
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();

        // 遍历所有映射关系
        handlerMethods.forEach((info, handlerMethod) -> {
            Set<String> urlPatterns = info.getPatternsCondition().getPatterns();
            Method method = handlerMethod.getMethod();
            urlPatterns.forEach(uri -> {
                if (!uri.startsWith("/")) {
                    uri = "/" + uri;
                }
                ArgusMethod argusMethod = new ArgusMethod(method);
                argusMethod.setMethod(method);
                argusMethod.setName(method.getName());
                argusMethod.setSignature(CommonUtil.generateSignature(method));
                argusMethod.setUri(uri);
                ArgusCache.addUriMethod(uri, argusMethod);
            });
        });
    }

    /**
     * 打印argus信息
     */
    private void printArgusInfo() {

        if (this.argusProperties.isPrintBanner()) {
            // 打印 argus banner
            try (InputStream inputStream = new ClassPathResource("META-INF/resources/argus/banner.txt").getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 打印用户信息
        if (this.argusProperties.isPrintUserInfo()) {

            if (userProvider instanceof ArgusUserProvider) {
                log.info("【Argus => username: {}, password: {}】",
                        ((ArgusUserProvider) userProvider).getUsername(),
                        ((ArgusUserProvider) userProvider).getPassword());
            }
        }
    }

    @Override
    public void run(String... args) throws Exception {

        log.info("【Argus => ArgusManager wrapper jdk proxy started...】");

        // 包装jdk代理, 提供动态刷新拦截器
        JdkProxyWrapper.wrapJdkProxies();

        log.info("【Argus => ArgusManager wrapper jdk proxy completed...】");
    }
}
