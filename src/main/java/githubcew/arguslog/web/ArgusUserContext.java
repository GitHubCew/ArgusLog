package githubcew.arguslog.web;

import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import org.springframework.stereotype.Component;

/**
 * 用户上下文
 *
 * @author chenenwei
 */
@Component
public class ArgusUserContext {

    /**
     * 构造方法
     */
    private ArgusUserContext() {}

    private static final ThreadLocal<ArgusUser> CURRENT_USER = new ThreadLocal<>();

    /**
     * 设置当前线程的用户上下文
     * @param argusUser 用户
     */
    public static void setCurrentUser (ArgusUser argusUser) {
        CURRENT_USER.set(argusUser);
    }

    /**
     * 设置当前线程的用户上下文
     * @param token 用户token
     */
    public static void setCurrentUser(String token) {
        ArgusUser user = ArgusCache.getUserToken(token);
        if (user != null) {
            setCurrentUser(user);
        }
    }

    /**
     * 获取当前用户
     * @return 用户
     */
    public static ArgusUser getCurrentUser() {
        return CURRENT_USER.get();
    }

    /**
     * 获取当前用户名
     * @return 用户名
     */
    public static String getCurrentUsername() {
        ArgusUser user = getCurrentUser();
        return user != null ? user.getToken().getToken() : null;
    }

    /**
     * 清除当前线程的用户上下文
     */
    public static void clearCurrentUser() {
        CURRENT_USER.remove();
    }

}
