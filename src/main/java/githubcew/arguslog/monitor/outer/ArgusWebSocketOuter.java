package githubcew.arguslog.monitor.outer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.MonitorOutput;
import githubcew.arguslog.monitor.WebRequestInfo;
import githubcew.arguslog.web.socket.ArgusSocketHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/**
 * WebSocket 输出器
 *
 * @author chenenwei
 */
public class ArgusWebSocketOuter implements Outer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ArgusWebSocketOuter() {
    }

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

                // 构建输出
                boolean sendNormal = buildNormalOutput(monitorInfo, monitorOutput, normalWrapper);
                boolean sendException = buildExceptionOutput(monitorOutput, exceptionWrapper);

                // 发送消息
                sendIfRequired(socketHandler, argusUser, sendNormal, ExecuteResult.SUCCESS, normalWrapper);
                sendIfRequired(socketHandler, argusUser, sendException, ExecuteResult.FAILED, exceptionWrapper);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 构建正常输出（method, uri, requestParam, methodParam, header, ip, result, time)
     *
     * @param monitorInfo   监听信息
     * @param monitorOutput 输出内容
     * @param wrapper       wrapper
     */
    private boolean buildNormalOutput(MonitorInfo monitorInfo, MonitorOutput monitorOutput, OutputWrapper wrapper) throws JsonProcessingException {

        boolean hasContent = monitorInfo.isIp() || monitorInfo.isHeader()
                || monitorInfo.isParam() || monitorInfo.isMethodParam()
                || monitorInfo.isResult() || monitorInfo.isTime()
                || monitorInfo.isUrl() || monitorInfo.isApi()
                || monitorInfo.isMethod() || monitorInfo.isType()
                ;
        if (!hasContent) {
            return false;
        }

        WebRequestInfo webRequestInfo = monitorOutput.getWebRequestInfo();
        if (monitorInfo.isUrl() || monitorInfo.isApi()) {
            wrapper.append("url => ").startCopy().append(webRequestInfo.getUrl()).endCopy().concat();
        }
        if (monitorInfo.isApi()) {
            wrapper.append("api => ").startCopy().append(monitorInfo.getArgusMethod().getUri()).endCopy().concat();
        }
        if (monitorInfo.isMethod()) {
            wrapper.append("method => ").append(monitorInfo.getArgusMethod().getSignature()).concat();
        }
        if (monitorInfo.isType()) {
            wrapper.append("type => ").append(webRequestInfo.getMethod()).concat();
        }
        if (monitorInfo.isIp()) {
            wrapper.append("ip => ").startCopy().append(webRequestInfo.getIp()).endCopy().concat();
        }
        // 请求头
        if (monitorInfo.isHeader()) {
            wrapper.append("header => ").append(webRequestInfo.getHeaders()).concat();
        }
        // 请求参数
        if (monitorInfo.isParam()) {
            wrapper.append("param => ").startCopy().append(webRequestInfo.getRawParams()).endCopy().concat();
        }
        // 方法参数
        if (monitorInfo.isMethodParam()) {
            // 方法参数
            wrapper.append("methodParam => ").startCopy();
            appendValue(wrapper.getBuilder(), objectMapper, monitorOutput.getMethodParam());
            wrapper.endCopy().concat();
        }
        // 结果
        if (monitorInfo.isResult()) {
            wrapper.append("result => ").startCopy();
            appendValue(wrapper.getBuilder(), objectMapper, monitorOutput.getResult());
            wrapper.endCopy().concat();
        }
        // 请求耗时
        if (monitorInfo.isTime()) {
            wrapper.append("time => ");
            appendValue(wrapper.getBuilder(), objectMapper, monitorOutput.getTime());
            wrapper.append(" ms").concat();
        }

        return true;
    }

    /**
     * 构建异常输出
     *
     * @param monitorOutput 输出内容
     * @param wrapper       wrapper
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
     * 发送消息
     *
     * @param handler    handler
     * @param user       用户
     * @param shouldSend 是否发送
     * @param code       code
     * @param wrapper    wrapper
     */
    private void sendIfRequired(ArgusSocketHandler handler, ArgusUser user, boolean shouldSend, int code, OutputWrapper wrapper) {
        String content = wrapper.build();
        if (shouldSend && content.length() > 0) {
            String data = content.endsWith(OutputWrapper.CONCAT) ?
                    content.substring(0, content.length() - OutputWrapper.CONCAT.length()) : content;
            data = data.replaceAll(OutputWrapper.CONCAT, OutputWrapper.LINE_SEPARATOR);
            String output = OutputWrapper.formatOutput(new ExecuteResult(code, data));
            handler.send(user.getSession(), output);
        }
    }

    /**
     * 序列化值
     *
     * @param sb     builder
     * @param mapper mapper
     * @param value  值
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
     *
     * @param sb builder
     * @param e  异常
     */
    public void appendException(StringBuilder sb, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        sb.append(sw);
    }
}