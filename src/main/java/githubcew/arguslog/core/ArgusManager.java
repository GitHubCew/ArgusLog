package githubcew.arguslog.core;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.config.ArgusConfigurer;
import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.web.auth.AccountAuthenticator;
import githubcew.arguslog.web.auth.TokenAuthenticator;
import githubcew.arguslog.web.auth.TokenProvider;
import githubcew.arguslog.core.cmd.CommandManager;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.web.extractor.Extractor;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.common.util.CommonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.*;

/**
 * Argus
 * @author chenenwei
 */
@Component
public class ArgusManager implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ArgusManager.class);

    private ArgusProperties argusProperties;
    private Extractor extractor;
    private UserProvider userProvider;
    private TokenProvider tokenProvider;
    private List<ArgusConfigurer> configurers = new ArrayList<>();
    private CommandManager commandManager;
    private ApplicationContext applicationContext;
    private AccountAuthenticator accountAuthenticator;
    private TokenAuthenticator tokenAuthenticator;


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

    public AccountAuthenticator getAccountAuthenticator() {
        return accountAuthenticator;
    }

    public TokenAuthenticator getTokenAuthenticator() {
        return tokenAuthenticator;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        this.applicationContext = event.getApplicationContext();
        this.configurers = new ArrayList<>(applicationContext.getBeansOfType(ArgusConfigurer.class).values());
        this.commandManager = applicationContext.getBean(CommandManager.class);
        this.extractor = applicationContext.getBean(Extractor.class);
        this.userProvider = applicationContext.getBean(UserProvider.class);
        this.tokenProvider = applicationContext.getBean(TokenProvider.class);
        this.requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        this.argusProperties = applicationContext.getBean(ArgusProperties.class);
        this.accountAuthenticator = applicationContext.getBean(AccountAuthenticator.class);
        this.tokenAuthenticator = applicationContext.getBean(TokenAuthenticator.class);
        // 注册bean
        init();

        // 扫描接口
        scan();

        // 打印Argus启动信息
        printArgusInfo();
    }

    /**
     * 初始化
     */
    public void init () {

        // configurers 排序
        this.configurers.sort(AnnotationAwareOrderComparator.INSTANCE);

        // 注册命令
        registerCommand();

        // 忽略鉴权命令
        registerIgnoreAuthorizationCommand();

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
     * 注册不需要认证命令
     */
    private void registerIgnoreAuthorizationCommand () {

        for (ArgusConfigurer configurer : configurers) {
            configurer.registerUnauthorizedCommands(this.commandManager);
        }
    }

    /**
     * 扫描接口
     */
    private void scan () {
        // 获取Spring MVC中所有的RequestMapping信息
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();

        // 遍历所有映射关系
        handlerMethods.forEach((info, handlerMethod) -> {
            Set<String> urlPatterns = info.getPatternsCondition().getPatterns();
            Method method = handlerMethod.getMethod();
            urlPatterns.forEach(uri ->{
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

        if (this.argusProperties.getPrintBanner()) {
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
        if (this.argusProperties.getPrintUserInfo()) {

            if (userProvider instanceof ArgusUserProvider) {
                log.info("【Argus => username: {}, password: {}】",
                        ((ArgusUserProvider) userProvider).getUsername(),
                        ((ArgusUserProvider) userProvider).getPassword());
            }
        }
    }
}
