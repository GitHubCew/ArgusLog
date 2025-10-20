package githubcew.arguslog.core.account;

import githubcew.arguslog.core.permission.Role;
import lombok.Data;

import java.util.List;
import java.util.Set;

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
    private Object extend;

    /**
     * 角色列表
     */
    private Set<String> roles;

    /**
     * 构造方法
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
     * @param roles 角色列表
     * @param extend 其他扩展信息
     *
     */
    public Account(String username, String password, String salt, Set<String> roles, Object extend) {
        this.username = username;
        this.password = password;
        this.salt = salt;
        this.extend = extend;
        this.roles = roles;
    }
}
