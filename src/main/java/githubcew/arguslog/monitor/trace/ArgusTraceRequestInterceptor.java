package githubcew.arguslog.monitor.trace;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorSender;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.web.socket.ArgusSocketHandler;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Argus trace 拦截器
 *
 * @author chenenwei
 */
public class ArgusTraceRequestInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestId = UUID.randomUUID().toString();
        ArgusTraceRequestContext.startRequest(requestId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

        String callTree = ArgusTraceRequestContext.getTreeStatistics();
        if (callTree.isEmpty()) {
            return;
        }
        Method method = ArgusTraceRequestContext.getStartMethod();
        MonitorSender monitorSender = ContextUtil.getBean(MonitorSender.class);
        ArgusSocketHandler argusSocketHandler = ContextUtil.getBean(ArgusSocketHandler.class);

        // 提交给MonitorSender线程池处理
        monitorSender.submit(() -> {
            List<String> userTokens = ArgusCache.getTraceUsersByMethod(new ArgusMethod(method));
            for (String token : userTokens) {
                ArgusUser user = ArgusCache.getUserToken(token);
                if (Objects.isNull(user) || !user.getSession().isOpen()) {
                    continue;
                }
                argusSocketHandler.send(user.getSession(), OutputWrapper.formatOutput(ExecuteResult.success(callTree)));
            }
        });
    }
}
