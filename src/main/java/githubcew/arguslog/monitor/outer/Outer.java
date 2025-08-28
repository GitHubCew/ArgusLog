package githubcew.arguslog.monitor.outer;

import githubcew.arguslog.monitor.MonitorOutput;

import java.lang.reflect.Method;

/**
 * 输出器
 *
 * @author chenenwei
 */
public interface Outer {

    /**
     * 输出日志
     *
     * @param method        调用的方法
     * @param monitorOutput 输出内容
     */
    void out(Method method, MonitorOutput monitorOutput);
}
