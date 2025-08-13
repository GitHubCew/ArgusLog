package githubcew.arguslog.business.outer;

import githubcew.arguslog.core.OutContent;

import java.lang.reflect.Method;

/**
 * 输出器
 * @author  chenenwei
 */
public interface Outer {

    /**
     * 输出日志
     * @param method 调用的方法
     * @param outContent 输出内容
     */
    void out(Method method, OutContent outContent);
}
