package githubcew.arguslog.core.method;

import lombok.Data;

/**
 * 方法监测信息类
 * @author  chenenwei
 */
@Data
public class MonitorInfo {

    /**
     * 方法
     */
    private ArgusMethod method;
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
     * @param method 方法
     * @param param 参数
     * @param result 结果
     * @param time 时间
     */
    public MonitorInfo (ArgusMethod method, boolean param, boolean result, boolean time, boolean exception, boolean callChain) {
        this.method = method;
        this.param = param;
        this.result = result;
        this.time = time;
        this.exception = exception;
        this.callChain = callChain;

    }

}
