package githubcew.arguslog.core.config;

import githubcew.arguslog.aop.MethodAdvice;
import githubcew.arguslog.aop.MethodPointcut;
import githubcew.arguslog.business.formater.ArguslogParamFormatter;
import githubcew.arguslog.business.socket.SocketHandler;
import githubcew.arguslog.business.outer.ArguslogWebSocketOuter;
import githubcew.arguslog.core.Constant;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
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
public class ArguslogAutoConfiguration implements ImportBeanDefinitionRegistrar, WebSocketConfigurer {

    @Qualifier("arguslogSocketHandler")
    @Autowired
    private SocketHandler socketHandler; // 直接注入

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
    public WebMvcConfigurer alogTerminalWebMvcConfigurer() {
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

        if (!registry.containsBeanDefinition("arguslogParamFormatter")) {
            registry.registerBeanDefinition("arguslogParamFormatter", defaultParamFormatterBuilder.getBeanDefinition());
        }

        BeanDefinitionBuilder defaultOuterBuilder = BeanDefinitionBuilder
                .rootBeanDefinition(ArguslogWebSocketOuter.class)
                .setScope(BeanDefinition.SCOPE_SINGLETON);

        if (!registry.containsBeanDefinition("arguslogWebSocketOuter")) {
            registry.registerBeanDefinition("arguslogWebSocketOuter", defaultOuterBuilder.getBeanDefinition());
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
