package githubcew.arguslog.config;

import githubcew.arguslog.aop.MethodAdvice;
import githubcew.arguslog.aop.MethodPointcut;
import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.monitor.formater.ArgusMethodParamFormatter;
import githubcew.arguslog.monitor.formater.MethodParamFormatter;
import githubcew.arguslog.monitor.outer.ArgusWebSocketOuter;
import githubcew.arguslog.monitor.outer.Outer;
import githubcew.arguslog.monitor.trace.ArgusTraceRequestInterceptor;
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
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 自动配置类
 */
@EnableConfigurationProperties(ArgusProperties.class)
@EnableWebSocket
@Configuration
@EnableAspectJAutoProxy
public class ArgusAutoConfiguration implements ImportBeanDefinitionRegistrar, WebSocketConfigurer, WebMvcConfigurer {

    @Qualifier("argusSocketHandler")
    @Autowired
    private ArgusSocketHandler argusSocketHandler;

    @Autowired
    private ArgusProperties argusProperties;

    /**
     * 注册请求体缓存过滤器(实现用户请求body参数重复读取)
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> requestBodyCacheFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>();

        // 定义过滤器：只包装需要的请求，并限制大小
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                // 判断是否有请求体
                String method = request.getMethod().toUpperCase();
                boolean hasBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);

                // 跳过 multipart（文件上传）
                String contentType = request.getContentType();
                boolean isMultipart = contentType != null && contentType.contains("multipart/");

                // 跳过无 body 或文件上传 或 超大请求
                if (!hasBody || isMultipart || contentLengthExceedsLimit(request)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // 包装请求
                ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
                filterChain.doFilter(wrappedRequest, response);
            }

            /**
             * 检查请求体大小是否超过限制
             */
            private boolean contentLengthExceedsLimit(HttpServletRequest request) {
                int contentLength = request.getContentLength();
                // 内容超过2M 或者内容未知
                return contentLength > 1024 * 1024 * 2 || contentLength == -1;
            }
        };

        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns("/*"); // 拦截所有路径
        registrationBean.setName("requestBodyCacheFilter");
        registrationBean.setOrder(0); // 最高优先级（在其他 filter 之前）
        registrationBean.setDispatcherTypes(javax.servlet.DispatcherType.REQUEST);

        return registrationBean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ArgusTraceRequestInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/actuator/**", "/argus/**");
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

    @ConditionalOnMissingBean(MethodParamFormatter.class)
    @Bean
    public MethodParamFormatter paramFormatter() {
        return new ArgusMethodParamFormatter();
    }

    @ConditionalOnMissingBean(Outer.class)
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
     *
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
