package githubcew.arguslog.config;

import githubcew.arguslog.aop.MethodAdvice;
import githubcew.arguslog.aop.MethodPointcut;
import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.monitor.formater.ArgusMethodParamFormatter;
import githubcew.arguslog.monitor.formater.MethodParamFormatter;
import githubcew.arguslog.monitor.outer.ArgusWebSocketOuter;
import githubcew.arguslog.monitor.outer.Outer;
import githubcew.arguslog.web.filter.ArgusFilter;
import githubcew.arguslog.web.filter.ArgusTraceRequestFilter;
import githubcew.arguslog.web.filter.RequestBodyCachingFilter;
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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.servlet.DispatcherType;

/**
 * 自动配置类
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
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
     * 注册请求体缓存过滤器。
     * 设置最高优先级，确保在其他过滤器（如 Shiro, Spring Security）之前执行。
     * @return RequestBodyCachingFilter
     * @see RequestBodyCachingFilter
     */
    @Bean
    public FilterRegistrationBean<RequestBodyCachingFilter> requestBodyCacheFilter() {
        FilterRegistrationBean<RequestBodyCachingFilter> registrationBean = new FilterRegistrationBean<>();

        // 创建并设置过滤器实例
        registrationBean.setFilter(new RequestBodyCachingFilter());
        registrationBean.addUrlPatterns("/*"); // 拦截所有路径
        registrationBean.setName("requestBodyCacheFilter");
        // 设置高优先级，确保尽早执行
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registrationBean.setDispatcherTypes(javax.servlet.DispatcherType.REQUEST);

        return registrationBean;
    }

    /**
     * 注册trace请求过滤器（要在RequestBodyCachingFilter之后执行）
     * @see RequestBodyCachingFilter
     * @return trace请求过滤器
     */
    @Bean
    public FilterRegistrationBean<ArgusTraceRequestFilter> argusTraceRequestFilter() {
        FilterRegistrationBean<ArgusTraceRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ArgusTraceRequestFilter());
        registration.addUrlPatterns("/*");
        // 在RequestBodyCachingFilter 之后执行
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    /**
     * Argus 过滤器
     *      处理页面 、token 登录逻辑
     * @return ArgusFilter
     */
    @Bean
    public FilterRegistrationBean<ArgusFilter> argusFilter () {
        FilterRegistrationBean<ArgusFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ArgusFilter());
        registration.addUrlPatterns("/*");
        // 在RequestBodyCachingFilter 之后执行
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }



    /**
     * 切点
     *
     * @return 切点
     */
    @Bean
    public MethodPointcut pointcut() {
        return new MethodPointcut();
    }

    /**
     * 通知
     *
     * @return 通知
     */
    @Bean
    public MethodAdvice advice() {
        return new MethodAdvice();
    }

    @Bean
    public MethodParamFormatter paramFormatter() {
        return new ArgusMethodParamFormatter();
    }

    @Bean
    public Outer outer() {
        return new ArgusWebSocketOuter();
    }

    /**
     * 默认切面
     *
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
     *
     * @return 用户提供者
     */
    @Bean
    @ConditionalOnMissingBean(UserProvider.class)
    public UserProvider userProvider() {
        return new ArgusUserProvider(argusProperties.getUsername(), argusProperties.getPassword());
    }

    /**
     * 默认token提供者
     *
     * @return token提供者
     */
    @Bean
    @ConditionalOnMissingBean(TokenProvider.class)
    public TokenProvider tokenProvider() {
        return new ArgusTokenProvider(argusProperties.getTokenExpireTime());
    }


    /**
     * Argus Servlet
     * @return ArgusServlet
     */
    @Bean
    public ArgusServlet argusServlet() {
        return new ArgusServlet();
    }

    /**
     * 默认请求解析器
     *
     * @return 请求解析器
     */
    @Bean
    public Extractor extractor() {
        return new ArgusRequestExtractor();
    }

    /**
     * 注册BeanDefinition
     *
     * @param importingClassMetadata importingClassMetadata
     * @param registry               registry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        scanPackages(registry, "githubcew.arguslog");
    }

    /**
     * 注册WebSocketHandler
     *
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
     *
     * @param registry     registry
     * @param basePackages basePackages
     */
    private void scanPackages(BeanDefinitionRegistry registry, String... basePackages) {
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry);
        scanner.scan(basePackages);
    }
}
