package githubcew.arguslog.web.filter;

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
import githubcew.arguslog.web.ArgusRequestContext;
import githubcew.arguslog.web.socket.ArgusSocketHandler;

import javax.servlet.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Argus trace 拦截器
 *
 * @author chenenwei
 */
public class ArgusTraceRequestFilter implements Filter {

    /**
     * 过滤器入口
     * @param request 请求
     * @param response 响应
     * @param chain 链
     * @throws IOException 异常
     * @throws ServletException 异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String requestId = UUID.randomUUID().toString();
        ArgusRequestContext.startRequest(requestId);

        try {
            chain.doFilter(request, response);
        }
        finally {

            try {
                String callTree = ArgusRequestContext.getTreeStatistics();
                Method startMethod = ArgusRequestContext.getStartMethod();

                if (!callTree.isEmpty() && startMethod != null) {
                    submitTraceData(callTree, startMethod);
                }
            } catch (Exception e) {
                // 忽略
            }
            // 清理上下文
            ArgusRequestContext.clear();
        }
    }

    /**
     * 提交 trace 数据给 MonitorSender 异步处理
     * @param callTree trace 数据
     * @param method 方法
     */
    private void submitTraceData(String callTree, Method method) {
        MonitorSender monitorSender = ContextUtil.getBean(MonitorSender.class);
        ArgusSocketHandler argusSocketHandler = ContextUtil.getBean(ArgusSocketHandler.class);

        monitorSender.submit(() -> {
            List<String> userTokens = ArgusCache.getTraceUsersByMethod(new ArgusMethod(method));
            for (String token : userTokens) {
                ArgusUser user = ArgusCache.getUserToken(token);
                if (Objects.isNull(user) || !user.getSession().isOpen()) {
                    continue;
                }

                MonitorInfo monitorInfo = ArgusCache.getTraceMonitorByUser(user.getToken().getToken(), method);
                StringUtil.ExtractionResult result = StringUtil.extractWithPositions(callTree);
                List<String> processValues = new ArrayList<>();

                if (monitorInfo != null && monitorInfo.getTrace() != null) {
                    for (String value : result.getValues()) {
                        try {
                            if (Integer.parseInt(value) > monitorInfo.getTrace().getColorThreshold()) {
                                processValues.add(ColorWrapper.red(value));
                            } else {
                                processValues.add(value);
                            }
                        } catch (NumberFormatException ignore) {
                            processValues.add(value);
                        }
                    }
                } else {
                    processValues.addAll(result.getValues());
                }

                String processTree = StringUtil.replaceBack(result, processValues);
                argusSocketHandler.send(
                        user.getSession(),
                        OutputWrapper.formatOutput(ExecuteResult.success(processTree))
                );
            }
        });
    }
}
