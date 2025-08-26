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
import java.util.*;

/**
 * WebSocket 输出器
 * @author chenenwei
 */
public class ArgusWebSocketOuter implements Outer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ArgusWebSocketOuter() {}

    @Override
    public void out(Method method, MonitorOutput monitorOutput) {
        Map<String, MonitorInfo> usersByMethod = ArgusCache.getUsersByMethod(method);
        ArgusSocketHandler socketHandler = ContextUtil.getBean(ArgusSocketHandler.class);

        usersByMethod.forEach((user, monitorInfo) -> {
            try {
                ArgusUser argusUser = ArgusCache.getUserToken(user);
                if (argusUser == null || !argusUser.getSession().isOpen()) {
                    return;
                }

                StringBuilder normalBuilder = new StringBuilder();
                StringBuilder exceptionBuilder = new StringBuilder();
                StringBuilder callChainBuilder= new StringBuilder();

                // 构建输出
                boolean sendNormal = buildNormalOutput(monitorInfo, monitorOutput, normalBuilder);
                boolean sendException = buildExceptionOutput(monitorOutput, exceptionBuilder);
                boolean sendCallChain = buildCallChainOutput(monitorInfo, monitorOutput, callChainBuilder);

                // 发送消息
                sendIfRequired(socketHandler, argusUser, sendNormal, ArgusConstant.SUCCESS, normalBuilder);
                sendIfRequired(socketHandler, argusUser, sendException, ArgusConstant.FAILED, exceptionBuilder);
                sendIfRequired(socketHandler, argusUser, sendCallChain, ArgusConstant.SUCCESS, callChainBuilder);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 构建正常输出（method, uri, param, result, time)
     * @param monitorInfo 监听信息
     * @param monitorOutput 输出内容
     * @param sb bulder
     */
    private boolean buildNormalOutput(MonitorInfo monitorInfo, MonitorOutput monitorOutput, StringBuilder sb) throws JsonProcessingException {
        sb.append("method => ").append(monitorInfo.getMethod().getSignature())
                .append(ArgusConstant.CONCAT_SEPARATOR);

        sb.append("uri => ")
                .append(ArgusConstant.COPY_START)
                .append(monitorInfo.getMethod().getUri())
                .append(ArgusConstant.COPY_END)
                .append(ArgusConstant.CONCAT_SEPARATOR);

        boolean hasContent = false;

        if (monitorInfo.isParam()) {
            sb.append("param => ")
                    .append(ArgusConstant.COPY_START);
            appendValue(sb, objectMapper, monitorOutput.getParam());
            sb.append(ArgusConstant.COPY_END)
                    .append(ArgusConstant.CONCAT_SEPARATOR);
            hasContent = true;
        }

        if (monitorInfo.isResult()) {
            sb.append("result => ")
                    .append(ArgusConstant.COPY_START);
            appendValue(sb, objectMapper, monitorOutput.getResult());
            sb.append(ArgusConstant.COPY_END)
                    .append(ArgusConstant.CONCAT_SEPARATOR);
            hasContent = true;
        }

        if (monitorInfo.isTime()) {
            sb.append("time => ");
            appendValue(sb, objectMapper, monitorOutput.getTime());
            sb.append(ArgusConstant.CONCAT_SEPARATOR);
            hasContent = true;
        }

        return hasContent;
    }

    /**
     * 构建异常输出
     * @param monitorOutput 输出内容
     * @param sb builder
     */
    private boolean buildExceptionOutput(MonitorOutput monitorOutput, StringBuilder sb) {
        Exception exception = monitorOutput.getException();
        if (exception == null) {
            return false;
        }

        sb.append("error => ");
        appendException(sb, exception);
        return true;
    }

    /**
     * 构建调用链输出
     * @param monitorInfo 监听信息
     * @param monitorOutput 输出内容
     * @param sb builder
     */
    private boolean buildCallChainOutput(MonitorInfo monitorInfo, MonitorOutput monitorOutput, StringBuilder sb) {
        if (!monitorInfo.isCallChain()) {
            return false;
        }

        StackTraceElement[] stackTrace = monitorOutput.getCallChain();
        if (stackTrace == null || stackTrace.length == 0) {
            return false;
        }

        ArgusProperties properties = ContextUtil.getBean(ArgusProperties.class);
        String excludePackages = properties.getCallChainExcludePackage();
        List<String> excludeList = CollectionUtils.isEmpty(Collections.singleton(excludePackages)) ?
                new ArrayList<>() : Arrays.asList(excludePackages.split(","));

        String filteredTrace = Arrays.stream(stackTrace)
                .filter(element -> !element.isNativeMethod() && notExcludePackage(element.getClassName(), excludeList))
                .map(Object::toString)
                .reduce((a, b) -> a + ArgusConstant.CONCAT_SEPARATOR + b)
                .orElse("");

        if (filteredTrace.isEmpty()) {
            return false;
        }

        sb.append("callChain => ").append(filteredTrace);
        return true;
    }

    /**
     * 发送消息
     * @param handler handler
     * @param user 用户
     * @param shouldSend 是否发送
     * @param code code
     * @param content 内容
     */
    private void sendIfRequired(ArgusSocketHandler handler, ArgusUser user, boolean shouldSend, int code, StringBuilder content) {
        if (shouldSend && content.length() > 0) {
            String data = content.toString().endsWith(ArgusConstant.CONCAT_SEPARATOR) ?
                    content.substring(0, content.length() - ArgusConstant.CONCAT_SEPARATOR.length()) : content.toString();
            data = data.replaceAll(ArgusConstant.CONCAT_SEPARATOR, ArgusConstant.LINE_SEPARATOR);
            String output = CommonUtil.formatOutput(new ExecuteResult(code, data));
            handler.send(user.getSession(), output);
        }
    }

    /**
     * 序列化值
     * @param sb builder
     * @param mapper mapper
     * @param value 值
     */
    private void appendValue(StringBuilder sb, ObjectMapper mapper, Object value) throws JsonProcessingException {
        if (value == null) {
            sb.append("null");
            return;
        }

        Class<?> clazz = value.getClass();
        if (clazz.isPrimitive() ||
                value instanceof Number ||
                value instanceof Boolean ||
                value instanceof Character ||
                value instanceof String) {
            sb.append(value);
        } else if (value.getClass().isArray()) {
            sb.append(Arrays.toString((Object[]) value));
        } else {
            sb.append(mapper.writeValueAsString(value));
        }
    }

    /**
     * 追加异常堆栈
     * @param sb builder
     * @param e 异常
     */
    public void appendException(StringBuilder sb, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        sb.append(sw);
    }

    /**
     * 检查类名是否在排除包中
     */
    public boolean notExcludePackage(String className, List<String> excludeList) {
        return excludeList.stream().noneMatch(className::startsWith);
    }
}