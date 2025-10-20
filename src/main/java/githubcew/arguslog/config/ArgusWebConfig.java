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
 * Argus Web 层配置类
 * - 注册 Filter、Servlet、WebSocket
 */
@Configuration
@EnableWebSocket
public class ArgusWebConfig implements WebSocketConfigurer {

    @Autowired
    private ArgusSocketHandler argusSocketHandler;

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

    @Bean
    public FilterRegistrationBean<ArgusTraceRequestFilter> argusTraceRequestFilter() {
        FilterRegistrationBean<ArgusTraceRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ArgusTraceRequestFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ArgusFilter> argusFilter() {
        FilterRegistrationBean<ArgusFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ArgusFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    @Bean
    public ArgusServlet argusServlet() {
        return new ArgusServlet();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(argusSocketHandler, "/argus-ws")
                .addInterceptors(new ArgusHandshakeInterceptor())
                .setAllowedOrigins("*");
    }
}
