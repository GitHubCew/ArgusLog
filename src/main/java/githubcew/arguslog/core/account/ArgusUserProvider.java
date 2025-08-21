package githubcew.arguslog.core.account;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * 用户信息提供者
 *      用户可以自定义提供账户密码，有两种方式：
 *      1.可通过在项目中配置属性自定义账户密码：
 *       eg:
 *           io.github.githubcew.argus.username = argus
 *           io.github.githubcew.argus.password = argus
 *      2.实现接口@link{githubcew.arguslog.core.account.UserProvider}
 *
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
