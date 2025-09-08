package githubcew.arguslog.web.filter;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
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
import java.util.*;

/**
 * Argus trace 拦截器
 *
 * @author chenenwei
 */
public class ArgusTraceRequestFilter implements Filter {

    /**
     * 过滤器入口
     *
     * @param request  请求
     * @param response 响应
     * @param chain    链
     * @throws IOException      异常
     * @throws ServletException 异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String requestId = UUID.randomUUID().toString();
        ArgusRequestContext.startRequest(requestId);

        try {
            chain.doFilter(request, response);
        } finally {

            try {
                ArgusRequestContext.MethodNode methodNode = ArgusRequestContext.getMethodNode();
                Method startMethod = ArgusRequestContext.getStartMethod(requestId);
                submitTraceTask(methodNode, startMethod);
            } catch (Exception e) {
                // 忽略
            }
            // 清理上下文
            ArgusRequestContext.clear();
        }
    }

    /**
     * 提交调用链输出任务
     * @param rootNode 根节点
     * @param method 开始方法
     */
    private void submitTraceTask(ArgusRequestContext.MethodNode rootNode, Method method) {
        MonitorSender monitorSender = ContextUtil.getBean(MonitorSender.class);
        ArgusSocketHandler argusSocketHandler = ContextUtil.getBean(ArgusSocketHandler.class);

        if (Objects.isNull(rootNode)) {
            return;
        }

        monitorSender.submit(() -> {
            List<String> userTokens = ArgusCache.getTraceUsersByMethod(new ArgusMethod(method));
            for (String token : userTokens) {
                ArgusUser user = ArgusCache.getUserToken(token);
                if (Objects.isNull(user) || !user.getSession().isOpen()) {
                    continue;
                }

                MonitorInfo monitorInfo = ArgusCache.getTraceMonitorByUser(user.getToken().getToken(), method);
                if (Objects.isNull(monitorInfo) || Objects.isNull(monitorInfo.getTrace())) {
                    continue;
                }

                String uri = ArgusCache.getMethodUri(new ArgusMethod(method));
                String tree = ArgusRequestContext.buildTreeString(rootNode, 0, monitorInfo.getTrace(), new ArrayList<>(), new HashMap<>());
                String methodSignature = CommonUtil.generateSignature(method);
                String output = "uri => " + OutputWrapper.wrapperCopy(uri)
                        + "\nmethod => " + methodSignature
                        + "\ntracing => "
                        + "\n" + tree;
                argusSocketHandler.send(
                        user.getSession(),
                        OutputWrapper.formatOutput(ExecuteResult.success(output))
                );
            }
        });
    }
}
