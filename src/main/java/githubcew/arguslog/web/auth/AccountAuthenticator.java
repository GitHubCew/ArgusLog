package githubcew.arguslog.web.auth;

import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.ArgusResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户认证器
 *
 * @author chenenwei
 */
@Component
public class AccountAuthenticator implements Authenticator {


    private final UserProvider userProvider;

    private final TokenProvider tokenProvider;

    /**
     * 构造方法
     *
     * @param userProvider  用户提供者
     * @param tokenProvider token提供者
     */
    @Autowired
    public AccountAuthenticator(UserProvider userProvider, TokenProvider tokenProvider) {
        this.userProvider = userProvider;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 认证
     *
     * @param request  请求
     * @param response 响应
     * @return 认证结果
     */
    @Override
    public boolean authenticate(ArgusRequest request, ArgusResponse response) {
        String username = request.getAccount().getUsername();
        String password = request.getAccount().getPassword();

        Account account = userProvider.provide(username);
        boolean isAuth = account.getUsername().equals(username) && account.getPassword().equals(password);
        if (isAuth) {
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
        return false;
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
     * 如果校验成功,立即返回
     *
     * @return 是否立即返回
     */
    @Override
    public boolean returnImmediately() {
        return true;
    }
}
