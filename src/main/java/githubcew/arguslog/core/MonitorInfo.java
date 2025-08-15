package githubcew.arguslog.core;

import java.lang.reflect.Method;

/**
 * 方法监测信息类
 * @author  chenenwei
 */
public class MonitorInfo {

    /**
     * 方法
     */
    private Method method;
    /**
     * 参数
     */
    private boolean param;
    /**
     * 结果
     */
    private boolean result;
    /**
     * 时间
     */
    private boolean time;
    /**
     * 异常
     */
    private boolean exception;

    private boolean stackTrace;

    /**
     * 构造方法
     */
    public MonitorInfo() {
    }

    /**
     * 构造方法
     * @param method 方法
     * @param param 参数
     * @param result 结果
     * @param time 时间
     * @param exception 异常
     * @param stackTrace 调用链
     */
    public MonitorInfo (Method method, boolean param, boolean result, boolean time, boolean exception,boolean stackTrace) {
        this.method = method;
        this.param = param;
        this.result = result;
        this.time = time;
        this.exception = exception;
        this.stackTrace = stackTrace;
    }

    /**
     * 获取方法
     * @return 方法
     */
    public Method getMethod() {
        return method;
    }

    /**
     * 设置方法
     * @param method 方法
     */
    public void setMethod(Method method) {
        this.method = method;
    }

    /**
     * 获取参数
     * @return 是否打印参数
     */
    public boolean isParam() {
        return param;
    }

    /**
     * 设置参数
     * @param param 参数
     */
    public void setParam(boolean param) {
        this.param = param;
    }

    /**
     * 获取结果
     * @return 是否打印结果
     */
    public boolean isResult() {
        return result;
    }

    /**
     * 设置结果
     * @param result 结果
     */
    public void setResult(boolean result) {
        this.result = result;
    }

    /**
     * 获取时间
     * @return 是否打印时间
     */
    public boolean isTime() {
        return time;
    }

    /**
     * 设置时间
     * @param time 时间
     */
    public void setTime(boolean time) {
        this.time = time;
    }

    /**
     * 获取异常
     * @return 是否打印异常
     */
    public boolean isException() {
        return exception;
    }

    /**
     * 设置异常
     * @param exception 异常
     */
    public void setException(boolean exception) {
        this.exception = exception;
    }

    public boolean isStackTrace() {
        return stackTrace;
    }
    /**
     * 设置异常
     * @param stackTrace 调用链
     */
    public void setStackTrace(boolean stackTrace) {
        this.stackTrace = stackTrace;
    }
}
