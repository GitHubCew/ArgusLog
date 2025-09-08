package githubcew.arguslog.web.filter;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.ArgusResponse;
import githubcew.arguslog.web.auth.AccountAuthenticator;
import githubcew.arguslog.web.auth.Token;
import org.apache.catalina.connector.RequestFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Augus 过滤器
 */
public class ArgusFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ArgusFilter.class);

    /**
     * 执行逻辑
     * @param request 请求
     * @param response 响应
     * @param chain 链
     * @throws IOException 异常
     * @throws ServletException 异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI().replace(((HttpServletRequest) request).getContextPath(), "");
        // 处理 /argus/** 请求
        if (uri.startsWith("/argus") && !uri.equals("/argus-ws")) {
            try {
                if (uri.endsWith("/index.html")) {
                    serveIndexHtml(httpRequest, httpResponse);
                } else if (uri.endsWith("/validateToken")) {
                    validateToken(httpRequest, httpResponse);
                } else if (uri.endsWith("/login")) {
                    handleLogin(httpRequest, httpResponse);
                } else {
                    httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
            } catch (Exception e) {
                logger.error("ArgusFilter 处理请求失败: " + uri, e);
                httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            return;
        }

        // 非 /argus/** 请求 → 放行给后续流程
        chain.doFilter(request, response);
    }

    /**
     * 处理/argus/index.html
     * @param request 请求
     * @param response 响应
     * @throws IOException 异常
     */
    private void serveIndexHtml(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String htmlPath = "META-INF/resources/argus/index.html";
        ClassPathResource resource = new ClassPathResource(htmlPath);

        if (!resource.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            String htmlContent = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            byte[] htmlBytes = htmlContent.getBytes(StandardCharsets.UTF_8);

            response.setContentType("text/html;charset=UTF-8");
            response.setContentLength(htmlBytes.length);
            response.getOutputStream().write(htmlBytes);
        }
    }

    /**
     * 验证token
     * @param request 请求
     * @param response 响应
     */
    private void validateToken(HttpServletRequest request, HttpServletResponse response) {
        String token = request.getHeader("argus-token");
        if (ArgusCache.hasToken(token)) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /**
     * 处理argus登录
     * @param request 请求
     * @param response 响应
     */
    private void handleLogin(HttpServletRequest request, HttpServletResponse response) {
        String username = request.getHeader("username");
        String password = request.getHeader("password");

        ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
        AccountAuthenticator accountAuthenticator = argusManager.getAccountAuthenticator();
        ArgusRequest argusRequest = new ArgusRequest();
        argusRequest.setAccount(new Account(username, password));

        if (!accountAuthenticator.supports(argusRequest)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ArgusResponse argusResponse = new ArgusResponse();
        boolean authenticate = accountAuthenticator.authenticate(argusRequest, argusResponse);

        if (authenticate) {
            Token token = argusResponse.getToken();
            response.addHeader("argus-token", token.getToken());
            response.addHeader("argus-token-expire-time", String.valueOf(token.getExpireTime()));
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
