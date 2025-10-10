package githubcew.arguslog.web.socket;

import githubcew.arguslog.ArgusStarter;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.web.ArgusUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Objects;

/**
 * WebSocket处理器
 *
 * @author chenenwei
 */
@Component("argusSocketHandler")
public class ArgusSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ArgusStarter argusStarter;

    /**
     * 构造方法
     */
    public ArgusSocketHandler() {
    }

    /**
     * 连接建立
     *
     * @param session session
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 添加session
        String token = (String) session.getAttributes().get("argus-token");
        ArgusUser argusUser = ArgusCache.getUserToken(token);
        if (!Objects.isNull(argusUser)) {
            argusUser.setSession(session);
            ArgusCache.addUserToken(token, argusUser);
            // 设置用户
            ArgusUserContext.setCurrentUser(argusUser);
        }
    }

    /**
     * 连接关闭
     *
     * @param session session
     * @param status  状态
     * @throws Exception 异常
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ArgusUser currentUser = ArgusCache.getUserBySession(session);
        if (Objects.isNull(currentUser)) {
            return;
        }
        ArgusUserContext.clearCurrentUser();
    }

    /**
     * 处理消息
     *
     * @param session session
     * @param message 消息
     * @throws Exception 异常
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String token = (String) session.getAttributes().get("argus-token");
        ArgusUser argusUser = ArgusCache.getUserToken(token);
        if (!Objects.isNull(argusUser)) {
            argusUser.setSession(session);
            ArgusCache.addUserToken(token, argusUser);
            // 设置用户
            ArgusUserContext.setCurrentUser(argusUser);
        }
        try {
            String result = argusStarter.start(session, message.getPayload());
            send(session, result);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(session, e.getMessage());
        }
    }

    /**
     * 发送消息
     *
     * @param session session
     * @param message 消息
     */
    public void send(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送失败消息
     *
     * @param session  session
     * @param errorMsg 消息
     */
    private void sendError(WebSocketSession session, String errorMsg) {
        try {
            if (session.isOpen()) {
                String output = OutputWrapper.formatOutput(ExecuteResult.failed(errorMsg));
                session.sendMessage(new TextMessage(output));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
