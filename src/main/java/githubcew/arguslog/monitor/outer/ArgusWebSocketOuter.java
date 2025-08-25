package githubcew.arguslog.monitor.outer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import githubcew.arguslog.common.constant.ArgusConstant;
import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.MonitorOutput;
import githubcew.arguslog.web.socket.ArgusSocketHandler;
import org.springframework.util.CollectionUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * websocket输出器
 * @author  chenenwei
 */
public class ArgusWebSocketOuter implements Outer{

    /**
     * 构造方法
     */
    public ArgusWebSocketOuter() {

    }
    ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 输出日志
     * @param method 调用的方法
     * @param monitorOutput 输出内容
     */
    @Override
    public void out(Method method, MonitorOutput monitorOutput) {

        Map<String, MonitorInfo> usersByMethod = ArgusCache.getUsersByMethod(method);
        ArgusSocketHandler argusSocketHandler = ContextUtil.getBean(ArgusSocketHandler.class);
        usersByMethod.forEach((user, monitorInfo) -> {
            try {
                ArgusUser argusUser = ArgusCache.getUserToken(user);
                if (Objects.isNull(argusUser) || !argusUser.getSession().isOpen()) {
                    return;
                }

                boolean sendNormal = false;
                boolean sendException = false;
                boolean sendCallChain = false;
                StringBuilder sb = new StringBuilder();
                StringBuilder err = new StringBuilder();
                StringBuilder callChain = new StringBuilder();
                sb.append("method => ").append(monitorInfo.getMethod().getSignature()).append(ArgusConstant.CONCAT_SEPARATOR);
                sb.append("uri => ").append(monitorInfo.getMethod().getUri()).append(ArgusConstant.CONCAT_SEPARATOR);

                if (monitorInfo.isParam()) {
                    sendNormal = true;
                    sb.append("param => ");
                    appendValue(sb, objectMapper, monitorOutput.getParam());
                    sb.append(ArgusConstant.CONCAT_SEPARATOR);
                }
                if (monitorInfo.isResult()) {
                    sendNormal = true;
                    sb.append("result => ");
                    appendValue(sb, objectMapper, monitorOutput.getResult());
                    sb.append(ArgusConstant.CONCAT_SEPARATOR);
                }
                if (monitorInfo.isTime()) {
                    sendNormal = true;
                    sb.append("time => ");
                    appendValue(sb, objectMapper, monitorOutput.getTime());
                    sb.append(ArgusConstant.CONCAT_SEPARATOR);
                }
                if (monitorOutput.getException() != null) {
                    sendException = true;
                    err.append("error => ");
                    appendException(err, monitorOutput.getException());
                }
                if (monitorInfo.isCallChain()) {
                    if (monitorOutput.getCallChain() != null) {
                        sendException = true;
                        appendCallChain(callChain, monitorOutput.getCallChain());
                    }
                }

                // 发送正常消息
                if (sendNormal) {
                    String data = sb.toString().replaceAll(ArgusConstant.CONCAT_SEPARATOR, ArgusConstant.LINE_SEPARATOR);
                    String output = CommonUtil.formatOutput(null, new ExecuteResult(ArgusConstant.SUCCESS, data));
                    argusSocketHandler.send(argusUser.getSession(), output);
                }
                // 发送异常消息
                if (sendException) {
                    String data =  err.toString().replaceAll(ArgusConstant.CONCAT_SEPARATOR, ArgusConstant.LINE_SEPARATOR);
                    String output = CommonUtil.formatOutput(null, new ExecuteResult(ArgusConstant.FAILED, data));
                    argusSocketHandler.send(argusUser.getSession(), output);
                }
                // 发送调用链消息
                if (sendException) {
                    String data =  callChain.toString().replaceAll(ArgusConstant.CONCAT_SEPARATOR, ArgusConstant.LINE_SEPARATOR);
                    String output = CommonUtil.formatOutput(null, new ExecuteResult(ArgusConstant.FAILED, data));
                    argusSocketHandler.send(argusUser.getSession(), output);
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
     * @param sb 输出内容
     * @param e 异常
     */
    public void appendException (StringBuilder sb, Exception e) {
        // 处理异常信息
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            sb.append(sw);
        }

    public void appendCallChain(StringBuilder sb,StackTraceElement[] stackTraceElement){
        ArgusProperties argusProperties = ContextUtil.getBean(ArgusProperties.class);
        String callChainExcludePackage = argusProperties.getCallChainExcludePackage();
        List<String> excludePackageList = (List<String>)CollectionUtils.arrayToList(callChainExcludePackage.split(","));

        //
        String str = String.join(ArgusConstant.LINE_SEPARATOR, Arrays.stream(stackTraceElement).filter(t->
                        !t.isNativeMethod() && notExcludePackage(t.getClassName(),excludePackageList) )
                .map(Object::toString).toArray(String[]::new));// 将堆栈信息输出到 PrintWriter
        sb.append(str);  // 转换为字符串并追加
    }

    // 不在排除包内  && !t.getClassName().startsWith("java.")   && !t.getClassName().startsWith("sun.")
    public boolean notExcludePackage(String  className,List<String> list){
        for (String startPackage : list) {
            if(className.startsWith(startPackage)){
                return false;
            }
        }
        return true;
    }



}
