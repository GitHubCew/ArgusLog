package githubcew.arguslog.business.socket;

import githubcew.arguslog.business.auth.ArgusAuthManager;
import githubcew.arguslog.business.auth.ArgusUser;
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
@Component("argusSocketHandler")
public class SocketHandler extends TextWebSocketHandler {

    @Qualifier("argusSessionManager")
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
        ArgusUser argusUser = new ArgusUser(session);
        sessionManager.addSession(session.getId(), argusUser);
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
            Object result = ArgusAuthManager.authenticateAndExecute(session, cmd);
            sendToClient(session, result.toString());
        } catch (Exception e) {
            session.sendMessage(new TextMessage(Constant.OUT_ERROR + e.getMessage()));
        }
    }

//    /**
//     * 执行命令
//     * @param cmd 命令
//     * @param argusUser 用户
//     * @throws IOException 异常
//     */
//    private void executeCommand(String cmd, ArgusUser argusUser) throws IOException {
//
//        WebSocketSession session = argusUser.getSession();
//        String userId = argusUser.getSessionId();
//        if (Objects.isNull(cmd) || cmd.isEmpty()) {
//            sendToClient(session, Constant.OUT_ERROR + "请输入命令");
//        }
////        else {
////            String[] parts = cmd.split(Constant.SPACE_PATTERN);
////            String trimCmd = parts[0];
////            Object result = "";
////            switch (trimCmd) {
////                case "lsm":
////                    LsMonitor lsm = new LsMonitor(cmd);
////                    result = lsm.exec(userId);
////                    sendToClient(session, format(result.toString()));
////                    break;
////                case "ls":
////                    Ls ls = new Ls(cmd);
////                    result = ls.exec(userId);
////                    sendToClient(session, format(result.toString()));
////                    break;
////                case "monitor":
////                    result = new Monitor(cmd).exec(userId);
////                    sendToClient(session, result.toString());
////                    break;
////                case "remove":
////                    result = new Remove(cmd).exec(userId);
////                    sendToClient(session, result.toString());
////                    break;
////                case "removeall":
////                    result = new RemoveAll(cmd).exec(userId);
////                    sendToClient(session, result.toString());
////                    break;
////                default:
////                    result = Constant.OUT_ERROR + "命令不支持";
////                    sendToClient(session, result.toString());
////                    break;
////            }
////        }
//    }

    /**
     * 发送消息到客户端
     * @param session session
     * @param message 消息
     */
    public void sendToClient(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(format(message)));
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
