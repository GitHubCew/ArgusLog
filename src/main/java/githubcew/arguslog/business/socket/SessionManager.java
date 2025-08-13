package githubcew.arguslog.business.socket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session管理器
 * @author  chenenwei
 */
@Component("arguslogSessionManager")
public class SessionManager {

    /**
     * 构造方法
     */
    public SessionManager() {
    }

    /**
     * sessionMap
     */
    private final Map<String, SessionContext> sessionMap = new ConcurrentHashMap<>();

    /**
     * 添加session
     * @param sessionId session id
     * @param context session context
     */
    public void addSession(String sessionId, SessionContext context) {
        sessionMap.put(sessionId, context);
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
     * @return session context
     */
    public SessionContext getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }
}
