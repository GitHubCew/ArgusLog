package githubcew.arguslog.invocation;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.monitor.MonitorSender;
import githubcew.arguslog.monitor.formater.ArgusMethodParamFormatter;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.web.socket.ArgusSocketHandler;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author Envi
 */
public class MqMethodInterceptor extends SafeMethodInterceptor{

    private final Logger log = LoggerFactory.getLogger(MqMethodInterceptor.class);

    private MonitorSender monitorSender;
    private ArgusSocketHandler argusSocketHandler;

    @Override
    public void beforeInvoke(MethodInvocation invocation) {
        safeInit();
        String message = buildMessage(invocation.getMethod(), invocation.getArguments(), null);
        sendToMonitors(invocation.getMethod(), OutputWrapper.formatOutput(ExecuteResult.success(message)));
    }

    @Override
    public void afterInvoke(MethodInvocation invocation, Object result) {

    }

    @Override
    public void afterThrowing(MethodInvocation invocation, Throwable e) {
        safeInit();
        String message = buildMessage(invocation.getMethod(), invocation.getArguments(), e);
        sendToMonitors(invocation.getMethod(), OutputWrapper.formatOutput(ExecuteResult.failed(message)));
    }

    private void safeInit() {
        try {
            init();
        } catch (Exception e) {
            log.error("Argus => init error: {}", e.getMessage(), e);
        }
    }

    private void init() {
        if (this.monitorSender == null) {
            ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
            this.monitorSender = argusManager.getMonitorSender();
        }
        if (this.argusSocketHandler == null) {
            this.argusSocketHandler = ContextUtil.getBean(ArgusSocketHandler.class);
        }
    }


    private String buildMessage(Method method, Object[] args, Throwable e) {
        StringBuilder sb = new StringBuilder();
        List<String> methodQueues = ArgusCache.getMethodQueues(method);

        sb.append("Argus MQ: \n");
        sb.append("queue => ").append(OutputWrapper.wrapperCopy(methodQueues, " ")).append("\n");
        sb.append("method => ").append(method.getDeclaringClass().getName()).append(".").append(method.getName()).append("\n");
        Object formatParam = new ArgusMethodParamFormatter().format(method.getParameters(), args);
        sb.append("param => ").append(formatParam).append("\n");
        if (e != null) {
            sb.append("error => ").append(CommonUtil.extractException(e)).append("\n");
        }
        return sb.toString();
    }

    private void sendToMonitors(Method method, String message) {
        monitorSender.submit(() -> {
            List<String> mqMonitorUser = ArgusCache.getMqMonitorUser(method);
            for (String user : mqMonitorUser) {
                ArgusUser argusUser = ArgusCache.getUserToken(user);
                if (argusUser != null && argusUser.getSession().isOpen()) {
                    argusSocketHandler.send(argusUser.getSession(), message);
                }
            }
        });
    }

}
