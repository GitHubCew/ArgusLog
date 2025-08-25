package githubcew.arguslog.core.account;

import lombok.Data;

/**
 * 用户信息提供者
 * @author chenenwei
 */
@Data
public class ArgusUserProvider implements UserProvider {

    // 用户名
    private String username;

    // 密码
    private String password;


    public ArgusUserProvider() {
    }

    public ArgusUserProvider(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * 自定义用户
     * @param username 用户名
     * @return 用户
     */
    @Override
    public Account provide(String username) {
        return new Account(this.username, this.password);
    }
}
