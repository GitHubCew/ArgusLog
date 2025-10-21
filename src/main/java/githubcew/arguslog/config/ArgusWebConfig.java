package githubcew.arguslog.config;

import githubcew.arguslog.web.filter.ArgusFilter;
import githubcew.arguslog.web.filter.ArgusTraceRequestFilter;
import githubcew.arguslog.web.filter.RequestBodyCachingFilter;
import githubcew.arguslog.web.servlet.ArgusServlet;
import githubcew.arguslog.web.socket.ArgusHandshakeInterceptor;
import githubcew.arguslog.web.socket.ArgusSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.servlet.DispatcherType;

/**
 * Argus Web 层配置类。
 * <div>
 *   负责注册 Argus 日志系统所需的核心 Web 组件，包括请求过滤器、自定义 Servlet 以及 WebSocket 端点。
 * </div>
 * <div>
 *   所有过滤器均作用于 {@code DispatcherType.REQUEST}，并通过 {@link org.springframework.core.Ordered}
 *   明确执行顺序，确保请求处理流程符合预期：先缓存请求体，再注入追踪上下文，最后执行日志记录。
 * </div>
 *
 * @author chenenwei
 */
@Configuration
@EnableWebSocket
public class ArgusWebConfig implements WebSocketConfigurer {

    @Autowired
    private ArgusSocketHandler argusSocketHandler;

    /**
     * 注册 {@link RequestBodyCachingFilter} 过滤器。
     * <div>
     *   该过滤器用于包装原始请求，使其输入流可重复读取，从而支持后续组件（如日志记录器）安全地读取请求体内容。
     * </div>
     * <div>
     *   执行顺序设为 {@code Ordered.HIGHEST_PRECEDENCE + 1}，确保在所有业务过滤器之前执行，
     *   为后续链路提供可缓存的请求体。
     * </div>
     *
     * @return 配置完成的 {@link FilterRegistrationBean} 实例
     */
    @Bean
    public FilterRegistrationBean<RequestBodyCachingFilter> requestBodyCacheFilter() {
        FilterRegistrationBean<RequestBodyCachingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RequestBodyCachingFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setName("requestBodyCacheFilter");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registrationBean.setDispatcherTypes(DispatcherType.REQUEST);
        return registrationBean;
    }

    /**
     * 注册 {@link ArgusTraceRequestFilter} 过滤器。
     * <div>
     *   该过滤器负责生成或透传分布式追踪标识（如 TraceID），并将上下文绑定到当前线程，
     *   便于在日志中关联同一请求链路下的所有操作。
     * </div>
     * <div>
     *   执行顺序为 {@code Ordered.HIGHEST_PRECEDENCE + 10}，位于请求体缓存之后、日志记录之前，
     *   确保追踪信息在记录日志时已就绪。
     * </div>
     *
     * @return 配置完成的 {@link FilterRegistrationBean} 实例
     */
    @Bean
    public FilterRegistrationBean<ArgusTraceRequestFilter> argusTraceRequestFilter() {
        FilterRegistrationBean<ArgusTraceRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ArgusTraceRequestFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    /**
     * 注册 {@link ArgusFilter} 过滤器。
     * <div>
     *   该过滤器是 Argus 日志系统的核心组件，负责采集 HTTP 请求与响应的元数据、耗时、状态码等信息，
     *   并异步写入日志存储。
     * </div>
     * <div>
     *   执行顺序为 {@code Ordered.HIGHEST_PRECEDENCE + 20}，在请求体缓存和追踪上下文初始化完成后执行，
     *   以确保能访问完整的请求内容与追踪标识。
     * </div>
     *
     * @return 配置完成的 {@link FilterRegistrationBean} 实例
     */
    @Bean
    public FilterRegistrationBean<ArgusFilter> argusFilter() {
        FilterRegistrationBean<ArgusFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ArgusFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    /**
     * 注册 {@link ArgusServlet}。
     * <div>
     *   提供一个专用的 HTTP 端点，用于查询或管理 Argus 日志系统的运行时状态（如健康检查、配置刷新等）。
     * </div>
     * <div>
     *   该 Servlet 由 Spring Boot 自动注册到内嵌容器中，默认路径由 Servlet 类内部注解或配置决定。
     * </div>
     *
     * @return {@link ArgusServlet} 实例
     */
    @Bean
    public ArgusServlet argusServlet() {
        return new ArgusServlet();
    }

    /**
     * 配置 WebSocket 端点及拦截器。
     * <div>
     *   注册 {@link ArgusSocketHandler} 处理路径为 {@code /argus-ws} 的 WebSocket 连接，
     *   用于向客户端实时推送日志或监控事件。
     * </div>
     * <div>
     *   添加 {@link ArgusHandshakeInterceptor} 拦截器，在 WebSocket 握手阶段注入用户身份或追踪上下文，
     *   并允许来自任意源（{@code *}}）的跨域连接（生产环境应限制具体域名）。
     * </div>
     *
     * @param registry WebSocket 处理器注册中心
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(argusSocketHandler, "/argus-ws")
                .addInterceptors(new ArgusHandshakeInterceptor())
                .setAllowedOrigins("*");
    }
}