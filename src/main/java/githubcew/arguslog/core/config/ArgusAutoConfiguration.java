package githubcew.arguslog.core.config;

import githubcew.arguslog.aop.MethodAdvice;
import githubcew.arguslog.aop.MethodPointcut;
import githubcew.arguslog.core.ArgusConstant;
import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.core.auth.ArgusTokenProvider;
import githubcew.arguslog.core.auth.TokenProvider;
import githubcew.arguslog.core.extractor.ArgusRequestExtractor;
import githubcew.arguslog.core.extractor.Extractor;
import githubcew.arguslog.core.formater.ArguslogParamFormatter;
import githubcew.arguslog.core.formater.ParamFormatter;
import githubcew.arguslog.core.outer.ArgusWebSocketOuter;
import githubcew.arguslog.core.outer.Outer;
import githubcew.arguslog.core.socket.ArgusSocketHandler;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 自动配置类
 */
@EnableConfigurationProperties(ArgusProperties.class)
@EnableWebSocket
@Configuration
@EnableAspectJAutoProxy
public class ArgusAutoConfiguration implements ImportBeanDefinitionRegistrar, WebSocketConfigurer {

    @Qualifier("argusSocketHandler")
    @Autowired
    private ArgusSocketHandler argusSocketHandler;

    @Autowired
    private ArgusProperties argusProperties;
    /**
     * 切点
     * @return 切点
     */
    @Bean
    public MethodPointcut pointcut() {return new MethodPointcut();}

    /**
     * 通知
     * @return 通知
     */
    @Bean
    public MethodAdvice advice() {return new MethodAdvice();}

    @ConditionalOnMissingBean(ParamFormatter.class)
    @Bean
    public ParamFormatter paramFormatter() {return new ArguslogParamFormatter();}

    @ConditionalOnMissingBean(Outer.class)
    @Bean
    public Outer outer () {return new ArgusWebSocketOuter();}
    /**
     * 默认切面
     * @return 切面
     */
    @Bean
    public DefaultPointcutAdvisor defaultPointcutAdvisor() {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut(), advice());
        advisor.setOrder(Integer.MAX_VALUE);
        return advisor;
    }

    /**
     * 默认用户提供者
     * @return 用户提供者
     */
    @Bean
    @ConditionalOnMissingBean(UserProvider.class)
    public UserProvider userProvider() {
        return new ArgusUserProvider(argusProperties.getUsername(), argusProperties.getPassword());
    }

    /**
     * 默认token提供者
     * @return token提供者
     */
    @Bean
    @ConditionalOnMissingBean(TokenProvider.class)
    public TokenProvider tokenProvider() {
        return new ArgusTokenProvider(argusProperties.getTokenExpireTime());
    }

    /**
     * 默认请求解析器
     * @return 请求解析器
     */
    @Bean
    @ConditionalOnMissingBean(Extractor.class)
    public Extractor extractor() {
        return new ArgusRequestExtractor();
    }

    @Order(0)
    @Bean
    public WebMvcConfigurer argusTerminalWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/argus/**")
                        .addResourceLocations("classpath:/" + ArgusConstant.BASE_RESOURCE_PATH)
                        .resourceChain(true);
            }
        };
    }

    /**
     * 注册BeanDefinition
     * @param importingClassMetadata importingClassMetadata
     * @param registry registry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        scanPackages(registry, "githubcew.arguslog");
    }

    /**
     * 注册WebSocketHandler
     * @param registry registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(argusSocketHandler, "/argus-ws").setAllowedOrigins("*");
    }

    /**
     * 扫描包
     * @param registry registry
     * @param basePackages basePackages
     */
    private void scanPackages(BeanDefinitionRegistry registry, String... basePackages) {
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry);
        scanner.scan(basePackages);
    }
}
