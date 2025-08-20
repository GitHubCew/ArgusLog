package githubcew.arguslog.core.auth;

import java.util.UUID;

/**
 * Argus token provider
 * @author chenenwei
 */

public class ArgusTokenProvider implements TokenProvider{

    @Override
    public Token provide() {
        // 默认过期为 10分钟
        return new Token(UUID.randomUUID().toString(), System.currentTimeMillis() + 1000 * 60 * 10);
    }
}
