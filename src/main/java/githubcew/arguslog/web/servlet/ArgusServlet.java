package githubcew.arguslog.web.servlet;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.ArgusResponse;
import githubcew.arguslog.web.auth.AccountAuthenticator;
import githubcew.arguslog.web.auth.Token;
import githubcew.arguslog.web.auth.TokenProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * ArgusServlet
 *
 * @author chenenwei
 */
public class ArgusServlet extends HttpServlet {

    /**
     * doGet
     * @param request 请求
     * @param response 响应
     * @throws IOException 异常
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // 访问web
        if (request.getRequestURI().endsWith("/argus/index.html")) {
            // 加载 HTML 文件
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
        // 验证token
        else if (request.getRequestURI().endsWith("/argus/validateToken")) {
            if (!ArgusCache.hasToken(request.getHeader("argus-token"))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    /**
     *  doGet
     * @param request 请求
     * @param response 响应
     * @throws IOException 异常
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        if (request.getRequestURI().endsWith("/argus/login")) {
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
}