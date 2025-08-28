package githubcew.arguslog.monitor;

import lombok.Data;

/**
 * 方法监测信息类
 *
 * @author chenenwei
 */
@Data
public class MonitorInfo {

    /**
     * 方法
     */
    private ArgusMethod method;
    /**
     * 请求头
     */
    private boolean header;

    /**
     * 请求ip
     */
    private boolean ip;
    /**
     * 请求参数
     */
    private boolean requestParam;
    /**
     * 方法参数
     */
    private boolean methodParam;
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

    /**
     * 方法调用链
     */
    private boolean callChain;

    /**
     * 构造方法
     */
    public MonitorInfo() {
    }

    /**
     * 构造方法
     *
     * @param method    方法
     * @param methodParam     参数
     * @param result    结果
     * @param time      时间
     * @param exception 异常
     * @param callChain 方法调用链
     */
    public MonitorInfo(ArgusMethod method, boolean methodParam, boolean result, boolean time, boolean exception, boolean callChain) {
        this.method = method;
        this.methodParam = methodParam;
        this.result = result;
        this.time = time;
        this.exception = exception;
        this.callChain = callChain;
    }

}
