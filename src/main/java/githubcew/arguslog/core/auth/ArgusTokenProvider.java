package githubcew.arguslog.core.auth;

import lombok.Data;

import java.util.UUID;

/**
 * Argus token provider
 * @author chenenwei
 */
@Data
public class ArgusTokenProvider implements TokenProvider{

    /**
     * token
     */
    String token = null;

    /**
     *  token过期时间
     */
    Long expireTime = 0L;

    public ArgusTokenProvider() {

    }

    public ArgusTokenProvider (Long expireTime) {
        this.token = UUID.randomUUID().toString();
        this.expireTime = System.currentTimeMillis() + expireTime;
    }

    public ArgusTokenProvider (String token, Long expireTime) {
        this.token = token;
        this.expireTime = expireTime;
    }

    @Override
    public Token provide() {
        return new Token(this.token, this.expireTime);
    }
}
