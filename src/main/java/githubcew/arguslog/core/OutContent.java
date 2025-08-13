package githubcew.arguslog.core;

/**
 * 输出内容
 * @author  chenenwei
 */
public class OutContent {

    /**
     * 接口参数
     */
    private Object param;

    /**
     * 接口耗时（毫秒）
     */
    private Long time;

    /**
     * 接口返回结果
     */
    private Object result;

    /**
     * 异常
     */
    private Exception exception;

    /**
     * 获取参数
     * @return 参数
     */
    public Object getParam() {
        return param;
    }

    /**
     * 设置参数
     * @param param 参数
     */
    public void setParam(Object param) {
        this.param = param;
    }

    /**
     * 获取接口耗时（毫秒）
     * @return 接口耗时（毫秒）
     */
    public Long getTime() {
        return time;
    }

    /**
     * 设置接口耗时（毫秒）
     * @param time 接口耗时（毫秒）
     */
    public void setTime(Long time) {
        this.time = time;
    }

    /**
     * 获取接口返回结果
     * @return 接口返回结果
     */
    public Object getResult() {
        return result;
    }

    /**
     * 设置接口返回结果
     * @param result 接口返回结果
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * 获取异常
     * @return 异常
     */
    public Exception getException() {
        return exception;
    }

    /**
     * 设置异常
     * @param exception 异常
     */
    public void setException(Exception exception) {
        this.exception = exception;
    }
}
