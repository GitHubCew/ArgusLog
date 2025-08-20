package githubcew.arguslog.core.account;

import lombok.Data;

/**
 * @author chenenwei
 */
@Data
public class Account {

    /**
     * 用户id
     */
    private String id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    public Account() {}

    public Account(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public Account(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }
}
