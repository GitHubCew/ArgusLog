package githubcew.arguslog.business.socket;

import githubcew.arguslog.business.auth.ArgusUser;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session管理器
 * @author  chenenwei
 */
@Component("argusSessionManager")
public class SessionManager {

    /**
     * 构造方法
     */
    public SessionManager() {
    }

    /**
     * sessionMap
     */
    private final Map<String, ArgusUser> sessionMap = new ConcurrentHashMap<>();

    /**
     * 添加session
     * @param sessionId session id
     * @param argusUser argusUser
     */
    public void addSession(String sessionId, ArgusUser argusUser) {
        sessionMap.put(sessionId, argusUser);
    }

    /**
     * 移除session
     * @param sessionId session id
     */
    public void removeSession(String sessionId) {
        sessionMap.remove(sessionId);
    }

    /**
     * 获取 session
     * @param sessionId session id
     * @return argusUser
     */
    public ArgusUser getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }
}
