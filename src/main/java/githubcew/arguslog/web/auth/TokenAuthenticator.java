package githubcew.arguslog.web.auth;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.ArgusResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author chenenwei
 */

@Component
public class TokenAuthenticator implements Authenticator {

    @Override
    public boolean authenticate(ArgusRequest request, ArgusResponse argusResponse) {
        return ArgusCache.hasToken(request.getToken().getToken());
    }

    @Override
    public boolean supports(ArgusRequest request) {
        Token token = request.getToken();
        return !Objects.isNull(token) && !Objects.isNull(token.getToken());
    }
}
