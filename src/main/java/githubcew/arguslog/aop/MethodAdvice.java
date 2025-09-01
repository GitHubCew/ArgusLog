package githubcew.arguslog.aop;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.monitor.MonitorSender;
import githubcew.arguslog.monitor.MonitorOutput;
import githubcew.arguslog.monitor.WebRequestInfo;
import githubcew.arguslog.monitor.formater.MethodParamFormatter;
import githubcew.arguslog.monitor.outer.Outer;
import githubcew.arguslog.web.extractor.RequestParamExtractor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * 方法增强
 *
 * @author chenenwei
 */
public class MethodAdvice implements MethodInterceptor {

    /**
     * 拦截方法
     *
     * @param invocation 方法调用
     * @return 返回值
     * @throws Throwable 异常
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        // 判断包方法
        boolean hasMethod = ArgusCache.containsMethod(invocation.getMethod());

        if (!hasMethod) {
            return invocation.proceed();
        }

        Object returnVal = null;
        MonitorOutput monitorOutput = new MonitorOutput();
        // 提取web请求参数
        try {
            WebRequestInfo webRequestInfo = RequestParamExtractor.extractRequestInfo();
            monitorOutput.setWebRequestInfo(webRequestInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }

        long start = 0;
        long end = 0;

        try {
            // 参数格式化
            MethodParamFormatter formatter = ContextUtil.getBean(MethodParamFormatter.class);
            Object format = formatter.format(invocation.getMethod().getParameters(), invocation.getArguments());
            monitorOutput.setMethodParam(format);
            // 调用链信息
            monitorOutput.setCallChain(new RuntimeException().getStackTrace());
            start = System.currentTimeMillis();

            // 执行原方法
            returnVal = invocation.proceed();

            monitorOutput.setResult(returnVal);
            end = System.currentTimeMillis();

        } catch (Exception e) {
            end = System.currentTimeMillis();
            monitorOutput.setException(e);
            throw e;
        } finally {
            // 计算耗时
            monitorOutput.setTime(end - start);
            // 使用线程池处理输出
            MonitorSender monitorSender = ContextUtil.getBean(MonitorSender.class);
            monitorSender.submit(() -> {
                // 输出content
                Outer outer = ContextUtil.getBean(Outer.class);
                outer.out(invocation.getMethod(), monitorOutput);
            });
        }

        return returnVal;
    }
}
