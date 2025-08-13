package githubcew.arguslog.business.socket;

import org.springframework.web.socket.WebSocketSession;


/**
 * Session上下文
 * @author  chenenwei
 */
public class SessionContext {

    /**
     * WebSocketSession
     */
    private WebSocketSession session;
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 连接状态
     */
    private boolean connected;

    /**
     * 构造方法
     * @param session WebSocketSession
     */
    public SessionContext(WebSocketSession session) {
        this.session = session;
        this.connected = false;
        this.userId = session.getId();
    }

    /**
     * 获取session
     * @return WebSocketSession
     */
    public WebSocketSession getSession() {
        return session;
    }

    /**
     * 设置session
     * @param session WebSocketSession
     */
    public void setSession(WebSocketSession session) {
        this.session = session;
    }

    /**
     * 获取用户ID
     * @return String
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 设置用户ID
     * @param userId String
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 连接状态
     * @return boolean
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 设置连接状态
     * @param connected boolean
     */
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

}