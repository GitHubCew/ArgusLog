package githubcew.arguslog.core.auth;

/**
 * token提供者
 * @author chenenwei
 */
public interface TokenProvider {

    /**
     * 提供token
     * @return token
     */
    Token provide ();
}
