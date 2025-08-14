package githubcew.arguslog.core.config;

import githubcew.arguslog.aop.MethodAdvice;
import githubcew.arguslog.aop.MethodPointcut;
import githubcew.arguslog.business.auth.ArgusUser;
import githubcew.arguslog.business.formater.ArguslogParamFormatter;
import githubcew.arguslog.business.socket.SocketHandler;
import githubcew.arguslog.business.outer.ArgusWebSocketOuter;
import githubcew.arguslog.core.Constant;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
@EnableWebSocket
@Configuration
@EnableAspectJAutoProxy
public class ArgusAutoConfiguration implements ImportBeanDefinitionRegistrar, WebSocketConfigurer {

    @Qualifier("argusSocketHandler")
    @Autowired
    private SocketHandler socketHandler;

    @Order
    @ConditionalOnMissingBean(ArgusUser.class)
    @Bean
    public ArgusUser argusUser() {return new ArgusUser("argus", "argus");}

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

    @Order(0)
    @Bean
    public WebMvcConfigurer argusTerminalWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/arguslog/**")
                        .addResourceLocations("classpath:/" + Constant.BASE_RESOURCE_PATH)
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

        registerBean(registry);
        scanPackages(registry, "githubcew.arguslog");
    }

    /**
     * 注册WebSocketHandler
     * @param registry registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socketHandler, Constant.WS_PATH).setAllowedOrigins("*");
    }

    /**
     * 注册Bean
     * @param registry registry
     */
    private void registerBean(BeanDefinitionRegistry registry) {

        BeanDefinitionBuilder defaultParamFormatterBuilder = BeanDefinitionBuilder
                .rootBeanDefinition(ArguslogParamFormatter.class)
                .setScope(BeanDefinition.SCOPE_SINGLETON);

        if (!registry.containsBeanDefinition("argusParamFormatter")) {
            registry.registerBeanDefinition("argusParamFormatter", defaultParamFormatterBuilder.getBeanDefinition());
        }

        BeanDefinitionBuilder defaultOuterBuilder = BeanDefinitionBuilder
                .rootBeanDefinition(ArgusWebSocketOuter.class)
                .setScope(BeanDefinition.SCOPE_SINGLETON);

        if (!registry.containsBeanDefinition("argusWebSocketOuter")) {
            registry.registerBeanDefinition("argusWebSocketOuter", defaultOuterBuilder.getBeanDefinition());
        }

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
