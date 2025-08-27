package githubcew.arguslog.monitor.outer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

                OutputWrapper normalWrapper = OutputWrapper.create();
                OutputWrapper exceptionWrapper = OutputWrapper.create();
                OutputWrapper callChainWrapper = OutputWrapper.create();

                // 构建输出
                boolean sendNormal = buildNormalOutput(monitorInfo, monitorOutput, normalWrapper);
                boolean sendException = buildExceptionOutput(monitorOutput, exceptionWrapper);
                boolean sendCallChain = buildCallChainOutput(monitorInfo, monitorOutput, callChainWrapper);

                // 发送消息
                sendIfRequired(socketHandler, argusUser, sendNormal, ExecuteResult.SUCCESS, normalWrapper);
                sendIfRequired(socketHandler, argusUser, sendException, ExecuteResult.FAILED, exceptionWrapper);
                sendIfRequired(socketHandler, argusUser, sendCallChain, ExecuteResult.SUCCESS, callChainWrapper);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 构建正常输出（method, uri, param, result, time)
     * @param monitorInfo 监听信息
     * @param monitorOutput 输出内容
     * @param wrapper wrapper
     */
    private boolean buildNormalOutput(MonitorInfo monitorInfo, MonitorOutput monitorOutput, OutputWrapper wrapper) throws JsonProcessingException {
        wrapper.append("method => ").append(monitorInfo.getMethod().getSignature()).concat();
        wrapper.append("uri => ").startCopy().append(monitorInfo.getMethod().getUri()).endCopy().concat();
        boolean hasContent = false;

        if (monitorInfo.isParam()) {
            wrapper.append("param => ").startCopy();
            appendValue(wrapper.getBuilder(), objectMapper, monitorOutput.getParam());
            wrapper.endCopy().concat();
            hasContent = true;
        }

        if (monitorInfo.isResult()) {
            wrapper.append("result => ").startCopy();
            appendValue(wrapper.getBuilder(), objectMapper, monitorOutput.getResult());
            wrapper.endCopy().concat();
            hasContent = true;
        }

        if (monitorInfo.isTime()) {
            wrapper.append("time => ");
            appendValue(wrapper.getBuilder(), objectMapper, monitorOutput.getTime());
            wrapper.concat();
            hasContent = true;
        }

        return hasContent;
    }

    /**
     * 构建异常输出
     * @param monitorOutput 输出内容
     * @param wrapper wrapper
     */
    private boolean buildExceptionOutput(MonitorOutput monitorOutput, OutputWrapper wrapper) {
        Exception exception = monitorOutput.getException();
        if (exception == null) {
            return false;
        }

        wrapper.append("error => ");
        appendException(wrapper.getBuilder(), exception);
        return true;
    }

    /**
     * 构建调用链输出
     * @param monitorInfo 监听信息
     * @param monitorOutput 输出内容
     * @param wrapper wrapper
     */
    private boolean buildCallChainOutput(MonitorInfo monitorInfo, MonitorOutput monitorOutput, OutputWrapper wrapper) {
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
                .reduce((a, b) -> a + OutputWrapper.CONCAT + b)
                .orElse("");

        if (filteredTrace.isEmpty()) {
            return false;
        }

        wrapper.append("callChain => ").append(filteredTrace);
        return true;
    }

    /**
     * 发送消息
     * @param handler handler
     * @param user 用户
     * @param shouldSend 是否发送
     * @param code code
     * @param wrapper wrapper
     */
    private void sendIfRequired(ArgusSocketHandler handler, ArgusUser user, boolean shouldSend, int code, OutputWrapper wrapper) {
        String content = wrapper.build();
        if (shouldSend && content.length() > 0) {
            String data = content.endsWith(OutputWrapper.CONCAT) ?
                    content.substring(0, content.length() - OutputWrapper.CONCAT.length()) : content;
            data = data.replaceAll(OutputWrapper.CONCAT, OutputWrapper.LINE_SEPARATOR);
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