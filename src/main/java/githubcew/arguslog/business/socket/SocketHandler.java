package githubcew.arguslog.business.socket;

import githubcew.arguslog.business.cmd.*;
import githubcew.arguslog.core.Constant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Objects;

/**
 * WebSocket处理器
 * @author  chenenwei
 */
@Component("arguslogSocketHandler")
public class SocketHandler extends TextWebSocketHandler {

    @Qualifier("arguslogSessionManager")
    @Autowired
    private SessionManager sessionManager;

    /**
     * 构造方法
     */
    public SocketHandler(){

    }
    /**
     * 连接建立
     * @param session session
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SessionContext context = new SessionContext(session);
        sessionManager.addSession(session.getId(), context);
    }

    /**
     * 连接关闭
     * @param session session
     * @param status 状态
     * @throws Exception 异常
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session.getId());
    }

    /**
     * 处理消息
     * @param session session
     * @param message 消息
     * @throws Exception 异常
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        String cmd = message.getPayload();
        try {
           executeCommand(cmd, sessionManager.getSession(session.getId()));
        } catch (Exception e) {
            session.sendMessage(new TextMessage(Constant.OUT_ERROR + e.getMessage() + Constant.LINE_SEPARATOR));
        }
    }

    /**
     * 执行命令
     * @param cmd 命令
     * @param context session上下文
     * @throws IOException 异常
     */
    private void executeCommand(String cmd, SessionContext context) throws IOException {

        WebSocketSession session = context.getSession();
        String userId = context.getUserId();
        if (Objects.isNull(cmd) || cmd.isEmpty()) {
            sendToClient(session, Constant.OUT_ERROR + "请输入命令");
        }
        else {
            String[] parts = cmd.split(Constant.SPACE_PATTERN);
            String trimCmd = parts[0];
            Object result = "";
            if (trimCmd.equals("lsm")) {
                LsMonitor lsm = new LsMonitor(cmd);
                result = lsm.exec(userId);
                sendToClient(session, format(result.toString()));
            }
            else if (trimCmd.equals("ls")) {
                Ls ls = new Ls(cmd);
                result = ls.exec(userId);
                sendToClient(session, format(result.toString()));
            }
            else if (trimCmd.equals("monitor")) {
                result = new Monitor(cmd).exec(userId);
                sendToClient(session, result.toString());
            }
            else if (trimCmd.equals("remove")) {
                result = new Remove(cmd).exec(userId);
                sendToClient(session, result.toString());
            }
            else if (trimCmd.equals("clearall")) {
                result = new RemoveAll(cmd).exec(userId);
                sendToClient(session, result.toString());
            }
            else {
                result = Constant.OUT_ERROR + "命令不支持";
                sendToClient(session, result.toString());
            }
        }
    }

    /**
     * 发送消息到客户端
     * @param session session
     * @param message 消息
     */
    public void sendToClient(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message + Constant.LINE_SEPARATOR));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 格式化
     * @param out  输出
     * @return 格式化后的输出
     */
    private String format (String out) {
        return out.replace(Constant.CONCAT_SEPARATOR, Constant.LINE_SEPARATOR);
    }
}
