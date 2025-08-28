package githubcew.arguslog.monitor;

import lombok.Data;

/**
 * 输出内容
 *
 * @author chenenwei
 */
@Data
public class MonitorOutput {

    /**
     * 方法参数
     */
    private Object methodParam;

    /**
     * 方法耗时（毫秒）
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
     * 调用链
     */
    StackTraceElement[] callChain;

    /**
     * web 请求信息
     */
    private WebRequestInfo webRequestInfo;
}
