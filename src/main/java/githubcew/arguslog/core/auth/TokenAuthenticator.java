package githubcew.arguslog.core.auth;

import githubcew.arguslog.core.ArgusCache;
import githubcew.arguslog.core.ArgusRequest;
import githubcew.arguslog.core.ArgusResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author chenenwei
 */

@Component
public class TokenAuthenticator implements Authenticator{

    @Override
    public boolean authenticate(ArgusRequest request, ArgusResponse argusResponse) {
        return ArgusCache.hasToken(request.getToken());
    }

    @Override
    public boolean supports(ArgusRequest request) {
        Token token = request.getToken();
        return !Objects.isNull(token) && !Objects.isNull(token.getToken());
    }
}
