package githubcew.arguslog.web.filter;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.common.util.RSAUtil;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.ArgusResponse;
import githubcew.arguslog.web.auth.ArgusAccountAuthenticator;
import githubcew.arguslog.web.auth.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

/**
 * Argus 过滤器
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
                } else if (uri.endsWith("/getPublicKey")) {
                    getPublicKey(httpRequest, httpResponse);
                }
                else {
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
     * 获取公钥
     * @param request 请求
     * @param response 响应
     */
    private void getPublicKey(HttpServletRequest request, HttpServletResponse response) {
        KeyPair keyPair = ContextUtil.getBean("argusKeyPair", KeyPair.class);
        // 返回公钥
        response.setHeader("argus-public-key", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
    }

    /**
     * 处理argus登录
     * @param request 请求
     * @param response 响应
     */
    private void handleLogin(HttpServletRequest request, HttpServletResponse response) {
        String username = request.getHeader("username");
        String password = request.getHeader("password");

        KeyPair keyPair = ContextUtil.getBean("argusKeyPair", KeyPair.class);
        String decryptPassword = RSAUtil.decryptWithPrivateKey(password, keyPair.getPrivate());

        ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
        ArgusAccountAuthenticator argusAccountAuthenticator = argusManager.getAccountAuthenticator();
        ArgusRequest argusRequest = new ArgusRequest();
        argusRequest.setAccount(new Account(username, decryptPassword));

        if (!argusAccountAuthenticator.supports(argusRequest)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ArgusResponse argusResponse = new ArgusResponse();
        boolean authenticate = argusAccountAuthenticator.authenticate(argusRequest, argusResponse);

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
