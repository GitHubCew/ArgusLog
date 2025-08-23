package githubcew.arguslog.core;

import githubcew.arguslog.core.*;
import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.auth.AccountAuthenticator;
import githubcew.arguslog.core.auth.Token;
import githubcew.arguslog.core.util.ContextUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author chenenwei
 */
public class ArgusServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // 访问web
        if ("/argus/index.html".equals(request.getRequestURI())) {
            // 加载 HTML 文件
            ClassPathResource resource = new ClassPathResource(ArgusConstant.ARGUS_TERMINAL_HTML);
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
        else if ("/argus/validateToken".equals(request.getRequestURI())) {
            if (!ArgusCache.hasToken(request.getHeader("argus-token"))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        if ("/argus/login".equals(request.getRequestURI())) {
            String username = request.getHeader("username");
            String password = request.getHeader("password");

            if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

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