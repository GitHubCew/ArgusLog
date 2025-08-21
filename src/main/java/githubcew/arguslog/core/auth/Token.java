package githubcew.arguslog.core.auth;

import lombok.Data;

/**
 * 凭证
 * @author chenenwei
 */
@Data
public class Token {

    /**
     * 凭证
     */
    private String token;

    /**
     * 凭证过期时间
     */
    private long expireTime;

    public Token() {}

    public Token(String token, Long expireTime) {
        this.token = token;
        this.expireTime = expireTime;
    }
}
