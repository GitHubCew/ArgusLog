package githubcew.arguslog.core.account;

import lombok.Data;

/**
 * @author chenenwei
 */
@Data
public class Account {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 盐值
     */
    private String salt;

    /**
     * 其他扩展信息
     */
    private Object extent;

    /**
     * 扩展方法
     */
    public Account() {}

    /**
     * 构造方法
     * @param username 用户名
     * @param password 密码
     */
    public Account(String username, String password) {
        this.username = username;
        this.password = password;
    }


    /**
     * 构造方法
     * @param username 用户名
     * @param password 密码
     * @param salt 盐值
     * @param extent 其他扩展信息
     */
    public Account(String username, String password, String salt, Object extent) {
        this.username = username;
        this.password = password;
        this.salt = salt;
        this.extent = extent;
    }
}
