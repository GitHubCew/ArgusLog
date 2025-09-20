package githubcew.arguslog.web.auth;

import lombok.Data;

import java.util.UUID;

/**
 * Argus token provider
 *
 * @author chenenwei
 */
@Data
public class ArgusTokenProvider implements TokenProvider {

    private Long expireTime;

    public ArgusTokenProvider(Long expireTime) {
        this.expireTime = expireTime;
    }

    @Override
    public Token provide(String username) {
        return new Token(UUID.randomUUID().toString(), System.currentTimeMillis() + this.expireTime * 1000);
    }
}
