package githubcew.arguslog.business.auth;

import org.springframework.web.socket.WebSocketSession;

/**
 * arguslog 用户信息
 * @author chenenwei
 */
public class ArgusUser {

    /**
     * sessionId
     */
    private String sessionId;
    /**
     * session
     */
    private WebSocketSession session;

    /**
     * token
     */
    private String token;

    /**
     * 账户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 构造方法
     */
    public ArgusUser() {
    }

    /**
     * 构造方法
     * @param session session
     */
    public ArgusUser (WebSocketSession session) {
        this.session = session;
        this.sessionId = session.getId();
    }

    /**
     * 构造方法
     * @param username username
     * @param password password
     */
    public ArgusUser (String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * 获取sessionId
     * @return sessionId
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 设置sessionId
     * @param sessionId sessionId
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 获取session
     * @return session
     */
    public WebSocketSession getSession() {
        return session;
    }

    /**
     * 设置session
     * @param session session
     */
    public void setSession(WebSocketSession session) {
        this.session = session;
    }

    /**
     * 获取用户名
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取密码
     * @return 密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码
     * @param password 密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取token
     * @return token
     */
    public String getToken() {
        return token;
    }

    /**
     * 设置token
     * @param token token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * 获取过期时间
     * @return 过期时间
     */
    public Long getExpireTime() {
        return expireTime;
    }

    /**
     * 设置过期时间
     * @param expireTime 过时时间
     */
    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }
}
