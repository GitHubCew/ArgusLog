package githubcew.arguslog.business.auth;

import org.springframework.lang.Nullable;

/**
 * 认证器
 * @author chenenwei
 */
public interface Authenticator {

    /**
     * 获取排序
     * @return 排序
     */
    @Nullable
    int order();

    /**
     * 认证
     * @param argusUser argusUser
     * @return 认证结果
     */
    boolean authenticate(ArgusUser argusUser);

}
