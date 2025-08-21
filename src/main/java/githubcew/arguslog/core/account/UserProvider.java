package githubcew.arguslog.core.account;

/**
 * @author chenenwei
 */
@FunctionalInterface
public interface UserProvider {

    Account provide (String username);
}
