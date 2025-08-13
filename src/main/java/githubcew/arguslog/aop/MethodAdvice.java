package githubcew.arguslog.aop;

import githubcew.arguslog.business.formater.ParamFormatter;
import githubcew.arguslog.core.ContextUtil;
import githubcew.arguslog.core.OutContent;
import githubcew.arguslog.core.Cache;
import githubcew.arguslog.business.outer.Outer;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * 方法增强
 * @author  chenenwei
 */
public class MethodAdvice implements MethodInterceptor {

    /**
     * 拦截方法
     * @param invocation 方法调用
     * @return 返回值
     * @throws Throwable 异常
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        boolean hasMethod = Cache.hasMethod(invocation.getMethod());

        OutContent content = new OutContent();
        Object returnVal  = null;
        if (!hasMethod) {
            returnVal = invocation.proceed();
            return returnVal;
        }
        try {
            // 参数格式化
            ParamFormatter formatter = ContextUtil.getBean(ParamFormatter.class);
            Object format = formatter.format(invocation.getMethod().getParameters(), invocation.getArguments());
            content.setParam(format);

            // 计时
            long start = System.currentTimeMillis();

            // 执行原方法
            returnVal = invocation.proceed();
            content.setResult(returnVal);

            // 计算方法耗时
            long spend = System.currentTimeMillis() - start;
            content.setTime(spend);

        }
        catch (Exception e) {
            content.setException(e);
            throw e;
        }
        finally {
            // 输出content
            Outer outer = ContextUtil.getBean(Outer.class);
            outer.out(invocation.getMethod(), content);
        }
        return returnVal;
    }
}
