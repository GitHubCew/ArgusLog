package githubcew.arguslog.invocation;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.monitor.MonitorOutput;
import githubcew.arguslog.monitor.WebRequestInfo;
import githubcew.arguslog.monitor.formater.MethodParamFormatter;
import githubcew.arguslog.monitor.outer.Outer;
import githubcew.arguslog.web.extractor.RequestParamExtractor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * @author chenenwei
 */
public class ApiMethodInterceptor extends SafeMethodInterceptor {

    private final MonitorOutput monitorOutput = new MonitorOutput();

    long start = 0;

    @Override
    public void beforeInvoke(MethodInvocation invocation) {
        // 提取web请求参数
        WebRequestInfo webRequestInfo = RequestParamExtractor.extractRequestInfo();
        monitorOutput.setWebRequestInfo(webRequestInfo);
        // 参数格式化
        MethodParamFormatter formatter = ContextUtil.getBean(MethodParamFormatter.class);
        Object format = formatter.format(invocation.getMethod().getParameters(), invocation.getArguments());
        monitorOutput.setMethodParam(format);
        // 调用链信息
        monitorOutput.setCallChain(new RuntimeException().getStackTrace());
        // 计时
        start = System.currentTimeMillis();

    }

    @Override
    public void afterInvoke(MethodInvocation invocation, Object object) {
        long end = System.currentTimeMillis();
        // 计算耗时
        monitorOutput.setTime(end - start);
        monitorOutput.setResult(object);
        // 使用线程池处理输出
        ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
        argusManager.getMonitorSender().submit(() -> {
            // 输出content
            Outer outer = ContextUtil.getBean(Outer.class);
            outer.out(invocation.getMethod(), monitorOutput);
        });
    }


    @Override
    public void afterThrowing(MethodInvocation invocation, Throwable e) {
        monitorOutput.setException((Exception) e);
        long end = System.currentTimeMillis();
        // 计算耗时
        monitorOutput.setTime(end - start);
        // 使用线程池处理输出
        ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
        argusManager.getMonitorSender().submit(() -> {
            // 输出content
            Outer outer = ContextUtil.getBean(Outer.class);
            outer.out(invocation.getMethod(), monitorOutput);
        });
    }
}

