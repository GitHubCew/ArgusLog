package githubcew.arguslog.config;

import githubcew.arguslog.aop.MethodAdvice;
import githubcew.arguslog.aop.MethodPointcut;
import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.monitor.formater.ArguslogParamFormatter;
import githubcew.arguslog.monitor.formater.ParamFormatter;
import githubcew.arguslog.monitor.outer.ArgusWebSocketOuter;
import githubcew.arguslog.monitor.outer.Outer;
import githubcew.arguslog.web.auth.ArgusTokenProvider;
import githubcew.arguslog.web.auth.TokenProvider;
import githubcew.arguslog.web.extractor.ArgusRequestExtractor;
import githubcew.arguslog.web.extractor.Extractor;
import githubcew.arguslog.web.servlet.ArgusServlet;
import githubcew.arguslog.web.socket.ArgusHandshakeInterceptor;
import githubcew.arguslog.web.socket.ArgusSocketHandler;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
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

    @Bean
    public ArgusServlet argusServlet() {
        return new ArgusServlet();
    }

    //Argus servlet
    @Bean
    public ServletRegistrationBean<ArgusServlet> argusLoginServletRegistration(ArgusServlet argusServlet) {
        return new ServletRegistrationBean<>(argusServlet, "/argus/index.html", "/argus/login", "/argus/validateToken");
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
                        .addResourceLocations("classpath:/META-INF/resources/argus/")
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
        registry.addHandler(argusSocketHandler, "/argus-ws")
                .addInterceptors(new ArgusHandshakeInterceptor())
                .setAllowedOrigins("*");
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
