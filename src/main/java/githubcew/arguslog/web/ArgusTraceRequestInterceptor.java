package githubcew.arguslog.web;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.common.util.StringUtil;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ColorWrapper;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
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

    /**
     * 拦截请求
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @return 是否通过
     * @throws Exception 异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestId = UUID.randomUUID().toString();
        ArgusRequestContext.startRequest(requestId);
        return true;
    }

    /**
     * 请求完成后执行逻辑
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @param ex 异常对象
     * @throws Exception 异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

        String callTree = ArgusRequestContext.getTreeStatistics();
        if (callTree.isEmpty()) {
            return;
        }

        Method method = ArgusRequestContext.getStartMethod();
        MonitorSender monitorSender = ContextUtil.getBean(MonitorSender.class);
        ArgusSocketHandler argusSocketHandler = ContextUtil.getBean(ArgusSocketHandler.class);

        // 提交给MonitorSender线程池处理
        monitorSender.submit(() -> {
            List<String> userTokens = ArgusCache.getTraceUsersByMethod(new ArgusMethod(method));
            for (String token : userTokens) {
                ArgusUser user = ArgusCache.getUserToken(token);
                if (Objects.isNull(user) || !user.getSession().isOpen()) {
                    continue;
                };
                MonitorInfo monitorInfo = ArgusCache.getTraceMonitorByUser(user.getToken().getToken(), method);
                StringUtil.ExtractionResult result = StringUtil.extractWithPositions(callTree);
                List<String> processValues = new ArrayList<>(result.getValues().size());
                if (!Objects.isNull(monitorInfo) && !Objects.isNull(monitorInfo.getTrace())) {
                    for (String value : result.getValues()) {
                        if (Integer.parseInt(value) > monitorInfo.getTrace().getColorThreshold()) {
                            processValues.add(ColorWrapper.red(value));
                        }
                        else {
                            processValues.add(value);
                        }
                    }
                }
                String processTree = StringUtil.replaceBack(result, processValues);

                argusSocketHandler.send(user.getSession(), OutputWrapper.formatOutput(ExecuteResult.success(processTree)));
            }
        });
    }
}
