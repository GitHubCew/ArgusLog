package githubcew.arguslog.business.auth;

import githubcew.arguslog.core.Cache;
import org.springframework.stereotype.Component;

/**
 *  Token 认证器
 *  @author chenenwei
 */
public class TokenAuthenticator implements Authenticator {

    @Override
    public int order() {
        return 1;
    }

    @Override
    public boolean authenticate(ArgusUser argusUser) {
        if (argusUser.getToken() == null || argusUser.getToken().isEmpty()) {
            return false;
        }
        // 判断是否包含 token
        return Cache.hasCredentials(argusUser.getToken());
    }
}
