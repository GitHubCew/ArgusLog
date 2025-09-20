package githubcew.arguslog.web.auth;

/**
 * token提供者
 *
 * @author chenenwei
 */
public interface TokenProvider {

    /**
     * 提供token
     *
     * @param username 用户名
     * @return token
     */
    Token provide(String username);
}
