package githubcew.arguslog.core.account;

/**
 * Author: Theo
 * Date: 16:43 2025/11/29
 */
@FunctionalInterface
public interface OperatorProvider {

    Account provide (String headers);
}
