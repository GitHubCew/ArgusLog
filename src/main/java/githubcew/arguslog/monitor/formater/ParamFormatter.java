package githubcew.arguslog.monitor.formater;

import java.lang.reflect.Parameter;

/**
 * 参数格式器
 * @author  chenenwei
 */
public interface ParamFormatter {

    /**
     * 参数格式化
     * @param parameters 参数列表
     * @param parameterValues 参数值列表
     * @return 格式化后的参数值
     */
    Object format(Parameter[] parameters, Object[] parameterValues);
}
