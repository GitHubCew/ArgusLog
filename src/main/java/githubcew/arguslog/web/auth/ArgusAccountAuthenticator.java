package githubcew.arguslog.web.auth;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.core.permission.ArgusPermissionConfigure;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.ArgusResponse;

import java.util.Objects;
import java.util.Set;

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

        ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
        if (!argusManager.isInitialized()) {
            return false;
        }
        ArgusProperties argusProperties = argusManager.getArgusProperties();
        UserProvider userProvider = argusManager.getUserProvider();
        TokenProvider tokenProvider = argusManager.getTokenProvider();

        Account account = new Account();
        String username = request.getAccount().getUsername();
        String password = request.getAccount().getPassword();
        account.setUsername(username);
        account.setPassword(password);

        // 设置角色
        Set<String> userRoles = argusManager.getArgusPermissionConfigure().getUserRoles(username);
        account.setRoles(userRoles);

        if (argusProperties.isEnableAuth()) {

            // 管理员用户
            if (Objects.equals(username, argusProperties.getUsername())) {
                if (!Objects.equals(password, argusProperties.getPassword())) {
                    return false;
                }
            }
            else {

                // 自定义用户
                boolean customize = customize(username, password, userProvider.provide(username));

                if (!customize) {
                    // 临时用户
                    Account tempUser = ArgusCache.getTempUser(username);
                    if (Objects.isNull(tempUser) || !tempUser.getPassword().equals(password)) {
                        return false;
                    }
                }
            }
        }

        // 构建返回token
        Token token = tokenProvider.provide(request.getAccount().getUsername());
        if (Objects.isNull(token)) {
            token = new ArgusTokenProvider(argusProperties.getTokenExpireTime()).provide(request.getAccount().getUsername());
        }
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
     * @param provide  用户信息
     * @return 认证结果
     */
    protected boolean customize(String username, String password, Account provide) {
        return provide.getUsername().equals(username) && provide.getPassword().equals(password);
    }
}
