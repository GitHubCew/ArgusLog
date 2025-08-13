package githubcew.arguslog.business.outer;

import githubcew.arguslog.business.socket.SessionContext;
import githubcew.arguslog.business.socket.SessionManager;
import githubcew.arguslog.business.socket.SocketHandler;
import githubcew.arguslog.core.Constant;
import githubcew.arguslog.core.ContextUtil;
import githubcew.arguslog.core.MonitorInfo;
import githubcew.arguslog.core.OutContent;
import githubcew.arguslog.core.Cache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/**
 * websocket输出器
 * @author  chenenwei
 */
public class ArguslogWebSocketOuter implements Outer{

    /**
     * 构造方法
     */
    public ArguslogWebSocketOuter() {

    }
    ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 输出日志
     * @param method 调用的方法
     * @param outContent 输出内容
     */
    @Override
    public void out(Method method, OutContent outContent) {

        Map<String, MonitorInfo> monitorOfUser = Cache.getUsersByMethod(method);
        SocketHandler socketHandler = ContextUtil.getBean(SocketHandler.class);
        monitorOfUser.forEach((userId, monitorInfo) -> {
            try {
                SessionContext session = ContextUtil.getBean(SessionManager.class).getSession(userId);
                if (!session.getSession().isOpen()) {
                    return;
                }
                StringBuilder sb = new StringBuilder(Constant.CONCAT_SEPARATOR);
                StringBuilder err = new StringBuilder(Constant.CONCAT_SEPARATOR);
                boolean sendNormal = false;
                boolean sendException = false;
                if (monitorInfo.isParam()) {
                    sendNormal = true;
                    sb.append("[PARAM] ");
                    appendValue(sb, objectMapper, outContent.getParam());
                    sb.append(Constant.CONCAT_SEPARATOR);
                }
                if (monitorInfo.isResult()) {
                    sendNormal = true;
                    sb.append("[RESULT] ");
                    appendValue(sb, objectMapper, outContent.getResult());
                    sb.append(Constant.CONCAT_SEPARATOR);
                }
                if (monitorInfo.isTime()) {
                    sendNormal = true;
                    sb.append("[TIME] ");
                    appendValue(sb, objectMapper, outContent.getTime());
                    sb.append(Constant.CONCAT_SEPARATOR);
                 }
                if (monitorInfo.isException()) {
                    if (outContent.getException() != null) {
                        sendException = true;
                        err.append("[ERROR] ");
                        appendException(err, outContent.getException());
                    }
                }

                // 发送正常消息
                if (sendNormal) {
                    socketHandler.sendToClient(session.getSession(), sb.toString().replaceAll(Constant.CONCAT_SEPARATOR, Constant.LINE_SEPARATOR));
                }
                // 发送异常消息
                if (sendException) {
                    socketHandler.sendToClient(session.getSession(), err.toString().replaceAll(Constant.CONCAT_SEPARATOR, Constant.LINE_SEPARATOR));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 处理各种类型的值
     * @param sb 输出内容
     * @param mapper 对象序列化器
     * @param value 值
     * @throws JsonProcessingException 序列化异常
     */
    private void appendValue(StringBuilder sb, ObjectMapper mapper, Object value) throws JsonProcessingException {
        if (value == null) {
            sb.append("null");
            return;
        }

        // 基本类型直接toString
        if (value.getClass().isPrimitive() ||
                value instanceof Number ||
                value instanceof Boolean ||
                value instanceof Character ||
                value instanceof String) {
            sb.append(value);
        }
        // 数组类型
        else if (value.getClass().isArray()) {
            sb.append(Arrays.toString((Object[])value));
        }
        // 其他对象类型使用JSON序列化
        else {
            sb.append(mapper.writeValueAsString(value));
        }
    }

    /**
     * 追加异常信息
     * @param sb
     * @param e
     */
    public void appendException (StringBuilder sb, Exception e) {
        // 处理异常信息
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);  // 将堆栈信息输出到 PrintWriter
            sb.append(sw);  // 转换为字符串并追加
        }

}
