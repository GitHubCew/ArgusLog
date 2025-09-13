package githubcew.arguslog.web.auth;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.ArgusResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户认证器
 *
 * @author chenenwei
 */

public class ArgusAccountAuthenticator implements Authenticator {

    /**
     * 认证
     *
     * @param request  请求
     * @param response 响应
     * @return 认证结果
     */
    @Override
    public boolean authenticate(ArgusRequest request, ArgusResponse response) {

        ArgusProperties argusProperties = ContextUtil.getBean(ArgusProperties.class);
        UserProvider userProvider = ContextUtil.getBean(UserProvider.class);
        TokenProvider tokenProvider = ContextUtil.getBean(TokenProvider.class);

        Account account = new Account();
        if (argusProperties.isEnableAuth()) {
            // 自定义认证
            String username = request.getAccount().getUsername();
            String password = request.getAccount().getPassword();
            boolean customize = customize(username, password, userProvider.provide(username));
            if (!customize) {
                return false;
            }
        }

        // 构建返回的tokne
        Token token = tokenProvider.provide();
        response.setToken(token);
        response.setExecuteResult(ExecuteResult.success(ExecuteResult.OK));

        // 添加用户到ArgusCache
        ArgusUser argusUser = new ArgusUser();
        argusUser.setAccount(account);
        argusUser.setSession(request.getSession());
        argusUser.setToken(token);
        ArgusCache.addUserToken(token.getToken(), argusUser);
        return true;
    }

    /**
     * 是否支持
     *
     * @param request 用户
     * @return 是否支持
     */
    @Override
    public boolean supports(ArgusRequest request) {
        return !Objects.isNull(request.getAccount())
                && !Objects.isNull(request.getAccount().getUsername())
                && !Objects.isNull(request.getAccount().getPassword());
    }


    /**
     * 自定义认证（可以继承 AccountAuthenticator 后重写此方法）
     *
     * @param username 用户名
     * @param password 密码
     * @return 认证结果
     */
    protected boolean customize(String username, String password, Account provide) {
        return provide.getUsername().equals(username) && provide.getPassword().equals(password);
    }
}
