package githubcew.arguslog.core;

import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.core.auth.Authenticator;
import githubcew.arguslog.core.auth.TokenProvider;
import githubcew.arguslog.core.cmd.ArgusCommand;
import githubcew.arguslog.core.cmd.CommandExecutor;
import githubcew.arguslog.core.cmd.CommandManager;
import githubcew.arguslog.core.extractor.Extractor;
import githubcew.arguslog.core.method.ArgusMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
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


    private Extractor extractor;
    private UserProvider userProvider;
    private TokenProvider tokenProvider;
    private List<Authenticator> authenticators = new ArrayList<>();
    private List<ArgusConfigurer> configurers = new ArrayList<>();
    private CommandManager commandManager;
    private ApplicationContext applicationContext;

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

    public List<Authenticator> getAuthenticators() {
        return authenticators;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }



    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        this.applicationContext = event.getApplicationContext();
        this.configurers = new ArrayList<>(applicationContext.getBeansOfType(ArgusConfigurer.class).values());
        this.commandManager = applicationContext.getBean(CommandManager.class);
        this.extractor = applicationContext.getBean(Extractor.class);
        this.userProvider = applicationContext.getBean(UserProvider.class);
        this.tokenProvider = applicationContext.getBean(TokenProvider.class);
        this.requestMappingHandlerMapping = applicationContext.getBean(RequestMappingHandlerMapping.class);

        ArgusCacheManager argusCacheManager = ArgusCacheManager.getInstance();

        // 注册bean
        init();

        // 扫描接口
        scan();

        // 启动缓存刷新线程
        argusCacheManager.start();

        // 打印Argus启动信息
        printArgusInfo();
    }

    /**
     * 初始化
     */
    public void init () {

        // configurers 排序
        this.configurers.sort(AnnotationAwareOrderComparator.INSTANCE);

        // 注册认证器
        this.authenticators = registerAuthenticator();

        // 注册命令
        registerCommand();

        // 认证器排序
        this.authenticators.sort(AnnotationAwareOrderComparator.INSTANCE);

    }

    /**
     * 初始化认证器列表
     * @return 认证器列表
     */
    private List<Authenticator> registerAuthenticator() {

        Set<Authenticator> authenticators = new HashSet<>();
        try {
            if (this.authenticators.isEmpty()) {
                authenticators.addAll(applicationContext.getBeansOfType(Authenticator.class).values());
            }
        }
        catch (NoSuchBeanDefinitionException e) {
            //
        }
        for (ArgusConfigurer configurer : configurers) {
            authenticators.addAll(configurer.registerAuthenticator(this.authenticators));
        }
        return new ArrayList<>(authenticators);
    }

    /**
     * 注册命令
     * @return 命令列表
     */
    private void registerCommand() {

        for (ArgusConfigurer configurer : configurers) {
            configurer.registerCommand(this.commandManager);
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
            urlPatterns.forEach(url ->{
                if (!url.startsWith("/")) {
                    url = "/" + url;
                }
                ArgusCache.addUriMethod(url, new ArgusMethod(method));
            });
        });
    }

    /**
     * 打印argus信息
     */
    private void printArgusInfo() {
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

        // 打印用户信息
        if (userProvider instanceof ArgusUserProvider) {
            log.info("Argus log started, username: {}, password: {}",
                    ((ArgusUserProvider) userProvider).getUsername(),
                    ((ArgusUserProvider) userProvider).getPassword());
        }
    }
}
